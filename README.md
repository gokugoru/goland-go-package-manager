, # Golang Advisor

A GUI plugin for managing Go libraries in IntelliJ-based IDEs and GoLand.

<!-- Plugin description -->
Golang Advisor provides a user-friendly interface for managing Go dependencies directly from your IDE.

**Features:**
- **View Installed Packages** - Display all packages from your go.mod file
- **Add New Packages** - Search and add Go packages with version selection
- **Update Packages** - Check for updates and upgrade to newer versions
- **Remove Packages** - Easily remove unused dependencies
- **Go Modules Integration** - Full support for Go Modules workflow

**Quick Start:**
1. Open the Golang Advisor tool window from the right sidebar
2. View your project's dependencies
3. Use the toolbar to add, update, or remove packages
<!-- Plugin description end -->

## Installation

### From JetBrains Marketplace
1. Open **Settings/Preferences** → **Plugins**
2. Search for "Golang Advisor"
3. Click **Install**

### Manual Installation
1. Download the latest release from [Releases](https://github.com/your-username/golang-advisor/releases)
2. Open **Settings/Preferences** → **Plugins**
3. Click **⚙️** → **Install Plugin from Disk...**
4. Select the downloaded `.zip` file

## Usage

### Opening the Tool Window
- Click the **Golang Advisor** icon in the right sidebar
- Or use the keyboard shortcut: `Alt+Shift+G`

### Managing Packages
- **Add Package**: Click ➕ or use the search field
- **Remove Package**: Select a package and click ➖
- **Update Package**: Select a package and click ⬇️
- **Update All**: Click the "Update All" button
- **Refresh**: Click 🔄 to reload the package list

## Requirements

- IntelliJ IDEA 2024.1+ or GoLand 2024.1+
- Go 1.16+ with Go Modules enabled
- A project with a valid `go.mod` file

## Building from Source

```bash
# Clone the repository
git clone https://github.com/your-username/golang-advisor.git
cd golang-advisor

# Build the plugin
./gradlew buildPlugin

# Run the plugin in a sandbox IDE
./gradlew runIde
```

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

Made with ❤️ for the Go community
