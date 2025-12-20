# Go Package Manager

A GUI plugin for managing Go dependencies in IntelliJ-based IDEs and GoLand.

<!-- Plugin description -->
Go Package Manager provides a user-friendly interface for managing Go dependencies directly from your IDE.

**Features:**
- View all packages from go.mod
- Add new packages via GitHub search
- Update packages to newer versions
- Remove unused dependencies
- See which packages are used in code vs transitive
<!-- Plugin description end -->

## Features

- **View Installed Packages** - Display all packages from your go.mod file
- **Add New Packages** - Search and add Go packages via GitHub
- **Update Packages** - Check for updates and upgrade to newer versions
- **Remove Packages** - Easily remove dependencies
- **Usage Detection** - See which packages are used in code vs transitive

## Installation

### From JetBrains Marketplace
1. Open **Settings/Preferences** > **Plugins**
2. Search for "Go Package Manager"
3. Click **Install**

### Manual Installation
1. Download the latest release from [Releases](https://github.com/gokugoru/goland-go-advisor/releases)
2. Open **Settings/Preferences** > **Plugins**
3. Click the gear icon > **Install Plugin from Disk...**
4. Select the downloaded `.zip` file

## Usage

### Opening the Tool Window
- Click the **Go Package Manager** icon in the right sidebar
- Or use the keyboard shortcut: `Alt+Shift+G`

### Managing Packages
- **Add Package**: Click + button, search on GitHub
- **Remove Package**: Select a package and click - button
- **Update Package**: Select a package with available update
- **Refresh**: Click refresh button to reload the list

## Requirements

- IntelliJ IDEA 2024.1+ or GoLand 2024.1+
- Go 1.16+ with Go Modules enabled
- A project with a valid `go.mod` file

## Building from Source

```bash
git clone https://github.com/gokugoru/goland-go-advisor.git
cd goland-go-advisor

# Build the plugin
./gradlew buildPlugin

# Run in sandbox IDE
./gradlew runIde
```

## License

MIT License - see [LICENSE](LICENSE) file.
