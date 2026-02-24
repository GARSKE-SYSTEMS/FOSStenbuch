# FOSStenbuch - Free Open Source Fahrtenbuch

[![Garske Systems](https://img.shields.io/badge/Developed%20by-Garske%20Systems-blue)](https://garske-systems.de)

FOSStenbuch is a free and open-source mileage logbook (Fahrtenbuch) app for Android, developed and maintained by [Garske Systems](https://garske-systems.de). It's designed to help users track their business and private trips for tax and reimbursement purposes.

## Features

- **Trip Tracking**: Record start/end locations, distances, purposes, and trip types
- **Vehicle Management**: Manage multiple vehicles with details
- **Statistics**: View comprehensive statistics and reports
- **Export**: Export trip data for tax purposes
- **Offline First**: All data stored locally on your device
- **Privacy Focused**: No tracking, no ads, no unnecessary permissions

## Tech Stack

- **Kotlin**: Primary programming language
- **Jetpack Compose**: Modern UI toolkit (planned for future)
- **Room**: Local database for trip and vehicle data
- **Hilt**: Dependency injection
- **Coroutines**: Asynchronous programming
- **Navigation Component**: In-app navigation
- **Timber**: Logging

## Architecture

The app follows clean architecture principles:

- **Data Layer**: Room database, repositories, DAOs
- **Domain Layer**: Business logic, use cases
- **Presentation Layer**: UI components, ViewModels

## Development Setup

### Prerequisites

- Android Studio (latest stable version)
- Java 17
- Android SDK 34
- Minimum SDK 26 (Android 8.0 Oreo)

### Building

1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/FOSStenbuch.git
   cd FOSStenbuch
   ```

2. Open in Android Studio
3. Build the project (Build > Make Project)

### Running Tests

```bash
# Unit tests
./gradlew test

# Android instrumented tests
./gradlew connectedAndroidTest
```

## CI/CD

The project uses GitHub Actions for:

- **Continuous Integration**: Runs on every push to main branch and PRs to main/release
- **Automated Testing**: Unit tests and instrumented tests
- **Release Builds**: Automated APK generation when PR to release branch is merged

### Release Process

1. **Development**: All work happens on feature branches, merged to `main` via PR
2. **Testing**: CI runs automatically on all PRs to `main`
3. **Release Preparation**:
   - Create a PR from `main` to `release` branch
   - Include version bump and release notes in the PR
   - Get required approvals
4. **Release Build**: When PR is merged, release workflow automatically:
   - Builds signed release APK
   - Uploads APK as workflow artifact
   - Available for download from GitHub Actions

**Important**: The `release` branch is PR-only - no direct pushes allowed!

## Contributing

Contributions are welcome! Please follow these steps:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/your-feature`)
3. Commit your changes (`git commit -m 'Add some feature'`)
4. Push to the branch (`git push origin feature/your-feature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Roadmap

- [ ] Basic trip CRUD operations
- [ ] Vehicle management
- [ ] Statistics dashboard
- [ ] Data export (CSV, PDF)
- [ ] Backup/restore functionality
- [ ] Dark mode support
- [ ] Multi-language support
- [ ] Widget for quick trip entry
- [ ] Mileage rate calculations
- [ ] Trip categorization and filtering

## Support

For questions, issues, or feature requests:
- Open an issue on GitHub
- Visit [Garske Systems website](https://garske-systems.de)
- Contact: support@garske-systems.de

## About Garske Systems

FOSStenbuch is developed and maintained by [Garske Systems](https://garske-systems.de), an IT consulting company specializing in custom software solutions, mobile applications, and open-source development. Our expertise includes:

- IT consulting and digital transformation
- Custom software development
- Mobile application development (Android & iOS)
- Open-source solutions and contributions
- Enterprise IT infrastructure

## Contributing

We welcome contributions from the community! If you'd like to contribute:

- Fork the repository
- Create a feature branch
- Submit a Pull Request
- Report bugs or suggest features via GitHub Issues

## Professional Services

As an IT consulting company, Garske Systems offers professional services for FOSStenbuch:

- **Customization and Integration**: Tailor FOSStenbuch to your business needs
- **Enterprise Deployment**: Large-scale rollout and management
- **Support Contracts**: Priority support and maintenance
- **Training**: User and administrator training
- **IT Consulting**: Digital transformation strategies

Contact us at [https://garske-systems.de](https://garske-systems.de) for consulting inquiries.

Thank you for using FOSStenbuch! 🚗💨