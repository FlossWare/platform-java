# Fix: "Authentication Required - System policy prevents management of local virtualized systems"

## Problem

You're seeing this error when trying to manage virtual machines with libvirt/KVM/QEMU:

```
Authentication Required
System policy prevents management of local virtualized systems
```

This is a PolicyKit (polkit) permission issue on Linux systems.

## Solutions

### Solution 1: Add User to libvirt Group (Recommended)

This is the cleanest and most secure solution.

```bash
# Add your user to the libvirt group
sudo usermod -aG libvirt $USER

# Also add to kvm group for good measure
sudo usermod -aG kvm $USER

# Log out and log back in for changes to take effect
# Or run this to apply immediately (in new shells):
newgrp libvirt
```

**After adding to group:**
- Log out completely and log back in
- Or reboot your system
- Verify with: `groups` (should see libvirt and kvm)

### Solution 2: PolicyKit Rule (Alternative)

Create a PolicyKit rule to allow your user to manage VMs without password.

**Create the rule file:**

```bash
sudo nano /etc/polkit-1/rules.d/50-libvirt.rules
```

**Add this content:**

```javascript
/* Allow users in libvirt group to manage VMs without authentication */
polkit.addRule(function(action, subject) {
    if (action.id == "org.libvirt.unix.manage" &&
        subject.isInGroup("libvirt")) {
            return polkit.Result.YES;
    }
});
```

**Or for a specific user:**

```javascript
/* Allow specific user to manage VMs without authentication */
polkit.addRule(function(action, subject) {
    if (action.id == "org.libvirt.unix.manage" &&
        subject.user == "sfloess") {
            return polkit.Result.YES;
    }
});
```

**Restart polkit:**

```bash
sudo systemctl restart polkit
```

### Solution 3: Use qemu:///system URI

Make sure you're connecting to the system URI, not session:

```bash
# Wrong (requires auth)
virsh -c qemu:///session list

# Correct (uses system daemon)
virsh -c qemu:///system list

# Or set as default
export LIBVIRT_DEFAULT_URI=qemu:///system
```

Add to your `~/.bashrc`:

```bash
echo 'export LIBVIRT_DEFAULT_URI=qemu:///system' >> ~/.bashrc
source ~/.bashrc
```

### Solution 4: GUI Tools (virt-manager)

For virt-manager specifically:

**Edit connection settings:**

1. Open virt-manager
2. File → Add Connection
3. Select "QEMU/KVM"
4. Check "Autoconnect"
5. Click "Connect"

**Or set default connection:**

```bash
# Edit virt-manager config
mkdir -p ~/.config/virt-manager
cat > ~/.config/virt-manager/virt-manager.conf << 'EOF'
[ui]
system_tray = 0

[connections]
uris = qemu:///system
autoconnect = qemu:///system
EOF
```

## Verification

### Check if libvirt is running:

```bash
sudo systemctl status libvirtd
```

If not running:

```bash
sudo systemctl enable --now libvirtd
```

### Check your groups:

```bash
groups
# Should show: ... libvirt kvm ...
```

### Test access:

```bash
virsh -c qemu:///system list --all
```

Should list VMs without asking for password.

### Check PolicyKit permissions:

```bash
pkaction --verbose --action-id org.libvirt.unix.manage
```

## Fedora/RHEL Specific

On Fedora 44+ and RHEL-based systems:

```bash
# Install libvirt if not already
sudo dnf install @virtualization

# Enable and start services
sudo systemctl enable --now libvirtd
sudo systemctl enable --now virtnetworkd

# Add user to groups
sudo usermod -aG libvirt $USER
sudo usermod -aG kvm $USER

# Reboot
sudo reboot
```

## Ubuntu/Debian Specific

```bash
# Install libvirt if not already
sudo apt install qemu-kvm libvirt-daemon-system libvirt-clients bridge-utils

# Add user to groups
sudo adduser $USER libvirt
sudo adduser $USER kvm

# Reboot
sudo reboot
```

## Arch Linux Specific

```bash
# Install libvirt if not already
sudo pacman -S libvirt qemu virt-manager

# Add user to groups
sudo usermod -aG libvirt $USER
sudo usermod -aG kvm $USER

# Enable and start services
sudo systemctl enable --now libvirtd

# Reboot
sudo reboot
```

## Troubleshooting

### Still getting authentication prompts?

**1. Check libvirt socket permissions:**

```bash
ls -la /var/run/libvirt/libvirt-sock
# Should show: srwxrwx--- 1 root libvirt
```

**2. Check PolicyKit configuration:**

```bash
# List all libvirt-related actions
pkaction | grep libvirt
```

**3. Check if polkit is running:**

```bash
sudo systemctl status polkit
```

**4. View PolicyKit rules:**

```bash
ls -la /etc/polkit-1/rules.d/
ls -la /usr/share/polkit-1/rules.d/
```

**5. Test with sudo (should work):**

```bash
sudo virsh list --all
```

If sudo works but regular user doesn't, it's definitely a permission issue.

### Nuclear option: Allow ALL users

**⚠️ Warning: Less secure, only use on single-user systems**

```bash
sudo nano /etc/polkit-1/rules.d/50-libvirt.rules
```

Add:

```javascript
/* Allow all users to manage VMs without authentication */
polkit.addRule(function(action, subject) {
    if (action.id == "org.libvirt.unix.manage") {
        return polkit.Result.YES;
    }
});
```

Restart polkit:

```bash
sudo systemctl restart polkit
```

## Quick Fix Summary

**For most users, do this:**

```bash
# 1. Add yourself to groups
sudo usermod -aG libvirt $USER
sudo usermod -aG kvm $USER

# 2. Log out and log back in (or reboot)
sudo reboot

# 3. Verify it works
virsh -c qemu:///system list --all
```

That should solve it! ✅

## References

- [libvirt documentation](https://libvirt.org/auth.html)
- [PolicyKit documentation](https://www.freedesktop.org/software/polkit/docs/latest/)
- [Fedora Virtualization Guide](https://docs.fedoraproject.org/en-US/quick-docs/virtualization-getting-started/)

---

**Created by:** Claude Code Automated Review System  
**Last Updated:** 2026-05-29
