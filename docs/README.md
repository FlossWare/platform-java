# Platform Java Documentation

## Troubleshooting Guides

### [Libvirt Authentication Fix](LIBVIRT_AUTHENTICATION_FIX.md)
Fix the "Authentication Required - System policy prevents management of local virtualized systems" error.

**Quick Fix:**
```bash
sudo usermod -aG libvirt $USER
sudo usermod -aG kvm $USER
sudo reboot
```

### [General Troubleshooting](TROUBLESHOOTING.md)
Common issues and solutions for platform-java.

## Architecture Documentation

### [Architecture Overview](ARCHITECTURE.md)
Complete platform architecture and design decisions.

### [Quick Reference](QUICK_REFERENCE.md)
Quick reference for common operations and commands.

---

**All documentation is available in this directory and linked from the main [README.md](../README.md).**
