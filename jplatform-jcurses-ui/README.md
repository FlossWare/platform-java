# JPlatform Terminal UI (JCurses-style)

Terminal-based management interface for JPlatform using Lanterna for curses-like text UI.

## Overview

The `jplatform-jcurses-ui` module provides a full-screen terminal interface for managing JPlatform instances. It uses Lanterna, a modern Java library that provides curses-like functionality without native dependencies. This UI is perfect for:

- SSH sessions and remote server management
- Headless environments where GUI is unavailable
- Users who prefer keyboard-driven interfaces
- Low-resource environments
- Terminal multiplexers (tmux, screen)

**Note:** Despite the module name, this uses Lanterna (not the old jcurses library) because:
- **Pure Java**: No native dependencies, works everywhere
- **Actively Maintained**: Regular updates and bug fixes
- **Better Features**: Modern terminal capabilities and Unicode support
- **Cross-Platform**: Works on Linux, macOS, Windows, and SSH sessions

## Features

- **Full-Screen Terminal UI**: Uses entire terminal window like htop, vim, or top
- **Keyboard Navigation**: All operations via keyboard shortcuts
- **Real-Time Metrics**: Live updates of CPU, memory, and thread count
- **Color-Coded States**: Visual indication of application status
  - Green = RUNNING
  - Yellow = STOPPED
  - Red = FAILED
  - White = DEPLOYED
- **Auto-Refresh**: Updates every 2 seconds automatically
- **Interactive Selection**: Navigate with arrow keys or vi-style (j/k)
- **Status Bar**: Help text and operation feedback

## User Interface

### Screen Layout

```
┌─────────────────────────────────────────────────────────────────────┐
│ JPlatform Terminal Console                                          │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│ Application ID       State        CPU (ms)        Heap (MB)  Threads│
│ ─────────────────────────────────────────────────────────────────── │
│ my-app               RUNNING      1234.56         52.30      15     │
│ another-app          STOPPED      0.00            0.00       0      │
│ web-service          RUNNING      5678.90         128.45     32     │
│                                                                      │
│                                                                      │
│                                                                      │
│                                                                      │
├─────────────────────────────────────────────────────────────────────┤
│ Keys: ↑/↓=navigate  s=start  t=stop  u=undeploy  r=refresh  q=quit │
│ Started: my-app | Apps: 3                                           │
└─────────────────────────────────────────────────────────────────────┘
```

### Keyboard Controls

| Key | Action |
|-----|--------|
| `↑` or `k` | Move selection up |
| `↓` or `j` | Move selection down |
| `s` | Start selected application |
| `t` | Stop selected application (t for "terminate") |
| `u` | Undeploy selected application |
| `r` | Refresh display |
| `d` | Deploy (shows message - use REST API or file watcher instead) |
| `q` | Quit terminal UI |

**Vi-style navigation**: `j` (down) and `k` (up) work just like arrow keys for vi/vim users.

## Usage

### Starting the Terminal UI

```bash
# Run with platform manager instance
java -cp jplatform-jcurses-ui-1.1.jar:jplatform-api-1.1.jar:jplatform-core-1.1.jar:lanterna-3.1.1.jar \
    org.flossware.jplatform.jcurses.TerminalConsole

# Or integrate with launcher (recommended)
java -jar jplatform-launcher-1.1.jar --terminal-ui
```

### Programmatic Usage

```java
import org.flossware.jplatform.api.PlatformManager;
import org.flossware.jplatform.core.ApplicationManager;
import org.flossware.jplatform.jcurses.TerminalConsole;

public class Main {
    public static void main(String[] args) throws IOException {
        PlatformManager manager = new ApplicationManager();
        TerminalConsole console = new TerminalConsole(manager);
        
        // Blocks until user presses 'q'
        console.start();
        
        // Clean up when done
        console.shutdown();
    }
}
```

### Terminal Requirements

**Minimum requirements:**
- Terminal emulator with ANSI color support
- Minimum size: 80x24 characters (standard terminal)
- UTF-8 encoding support (recommended)

**Tested terminals:**
- Linux: gnome-terminal, konsole, xterm, kitty, alacritty
- macOS: Terminal.app, iTerm2
- Windows: Windows Terminal, Git Bash, Cygwin
- SSH: Works over SSH sessions

**Terminal multiplexers:**
- tmux: Fully supported
- GNU screen: Fully supported

### Example Workflow

1. **Launch Terminal UI**
   ```bash
   java -jar jplatform-launcher-1.1.jar --terminal-ui
   ```

2. **Navigate Applications**
   - Use `↑`/`↓` or `j`/`k` to move selection
   - Selected row is highlighted in white

3. **Start Application**
   - Navigate to a DEPLOYED or STOPPED app
   - Press `s`
   - Status bar shows "Started: app-name"

4. **View Metrics**
   - Metrics auto-update every 2 seconds
   - Watch CPU time, heap usage, thread count in real-time

5. **Stop Application**
   - Navigate to a RUNNING app
   - Press `t`
   - Status bar shows "Stopped: app-name"

6. **Undeploy Application**
   - Navigate to any app
   - Press `u`
   - Application is removed from the list

7. **Quit**
   - Press `q` to exit terminal UI

## Deployment

**Note:** Deployment via terminal UI is not supported. Use one of these methods instead:

- **REST API**: `curl -X POST http://localhost:8080/api/applications -d '{...}'`
- **File Watcher**: Drop YAML descriptor in watched directory
- **Programmatic API**: `platformManager.deploy(descriptor)`

Pressing `d` in the terminal UI displays this message as a reminder.

## Color Scheme

The terminal UI uses ANSI colors for visual feedback:

| Color | Meaning |
|-------|---------|
| Blue (background) | Title bar |
| Cyan | Column headers |
| Green | RUNNING applications |
| Yellow | STOPPED applications, Help text |
| Red | FAILED applications, Errors |
| White | DEPLOYED applications, Normal text |
| Black on White | Selected row (highlighted) |

## Configuration

### Auto-Refresh Interval

Default: 2 seconds. To customize, modify `TerminalConsole.java`:

```java
refreshExecutor.scheduleAtFixedRate(..., 2, 2, TimeUnit.SECONDS);
//                                          ↑  ↑
//                                  initial delay  period
```

### Terminal Size

The UI adapts to terminal size automatically. Resize your terminal window and the UI adjusts.

**Recommended:** 80x24 or larger for best experience.

## Comparison with Other UIs

| Feature | Terminal UI | Swing UI | Web Console |
|---------|------------|----------|-------------|
| **Environment** | Terminal/SSH | Desktop | Browser |
| **Dependencies** | Lanterna (pure Java) | Java Swing | REST API |
| **Remote Access** | SSH | VNC/RDP | HTTP |
| **Resource Usage** | Very Low | Low | Medium |
| **Graphics** | Text-based | Native GUI | HTML/CSS/JS |
| **Keyboard-Driven** | Yes | Partial | Partial |
| **Mouse Support** | No | Yes | Yes |
| **Deployment UI** | No (use API) | Yes (file chooser) | Yes (form) |
| **Best For** | Servers, SSH | Desktop users | Multi-user, remote |

## Troubleshooting

### "Terminal not available" Error

**Cause:** No TTY available (running in CI/CD, cron, systemd service)

**Solution:** Terminal UI requires an interactive terminal. Use REST API or programmatic API instead.

### Garbled Display

**Cause:** Terminal doesn't support ANSI escape codes

**Solution:** Use a modern terminal emulator (see Terminal Requirements above).

### Colors Not Working

**Cause:** Terminal color support disabled

**Solution:**
```bash
# Check TERM variable
echo $TERM

# Should be something like: xterm-256color, screen-256color
# If not, set it:
export TERM=xterm-256color
```

### UI Doesn't Resize

**Cause:** Some terminals don't send resize signals properly

**Solution:** Press `r` to manually refresh after resizing.

### Arrow Keys Not Working

**Cause:** Terminal input mode issue

**Solution:** Use vi-style keys: `j` (down), `k` (up) instead.

## Testing

Run tests with:

```bash
mvn test
```

**Test Coverage Notes:**
- Terminal UI tests are limited in headless CI/CD environments
- Tests focus on API contract validation (constructor parameter checks)
- Full UI testing requires interactive terminal (manual testing)

Manual test checklist:
- [ ] Terminal displays properly with colors
- [ ] Arrow key navigation works
- [ ] Vi-style (j/k) navigation works
- [ ] Start command works for DEPLOYED/STOPPED apps
- [ ] Stop command works for RUNNING apps
- [ ] Undeploy command works
- [ ] Refresh command works
- [ ] Quit command exits cleanly
- [ ] Auto-refresh updates metrics every 2 seconds
- [ ] Selection highlighting visible
- [ ] Status bar updates with operation feedback
- [ ] Terminal resize handled correctly

## Dependencies

- `jplatform-api` - Platform management API
- `lanterna` 3.1.1 - Terminal UI library
- `slf4j-api` - Logging
- Java 21+

## Limitations

- **No Deployment UI**: Use REST API or file watcher to deploy applications
- **No Mouse Support**: Keyboard-only navigation
- **No Dialog Boxes**: All feedback via status bar
- **Single User**: No multi-user sessions
- **No History**: Metrics not persisted (resets on restart)

## Future Enhancements

- Mouse support for terminal emulators that support it
- Deployment form (multi-screen input)
- Application log viewer (tail -f style)
- Configuration editor
- Search/filter applications
- Pagination for large application lists
- Metrics history graphs (using text-based charts)

## See Also

- [REST API](../jplatform-rest-api/README.md) - For deployment and programmatic control
- [Web Console](../jplatform-web-console/README.md) - Browser-based alternative
- [Swing UI](../jplatform-swing-ui/README.md) - Desktop GUI alternative
- [Lanterna Documentation](https://github.com/mabe02/lanterna) - Terminal UI library
- [QUICKSTART](../QUICKSTART.md) - Quick start guide
- [Main README](../README.md) - Platform overview
