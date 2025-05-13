# Contributing to JavaFlow

Thank you for your interest in contributing to JavaFlow! This document provides guidelines and instructions for contributing to the project.

## Table of Contents
- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Environment Setup](#development-environment-setup)
- [Build and Test](#build-and-test)
- [Coding Standards](#coding-standards)
- [Pull Request Process](#pull-request-process)
- [Commit Message Guidelines](#commit-message-guidelines)
- [Issue Reporting Guidelines](#issue-reporting-guidelines)
- [Documentation](#documentation)
- [License](#license)

## Code of Conduct

Please read and follow our [Code of Conduct](CODE_OF_CONDUCT.md) to foster an inclusive and respectful community.

## Getting Started

1. **Fork the Repository**: Start by forking the repository to your own GitHub account.
2. **Clone the Repository**: Clone your fork locally.
   ```
   git clone https://github.com/your-username/javaflow.git
   cd javaflow
   ```
3. **Set up Remote**: Add the upstream repository as a remote to keep your fork in sync.
   ```
   git remote add upstream https://github.com/panghy/javaflow.git
   ```

## Development Environment Setup

### Prerequisites
- JDK 21 or later (required for Continuation support)
- Gradle 8.14 or later

### IDE Setup
We recommend using IntelliJ IDEA or another IDE with good Java and Gradle support. Key setup considerations:

1. **Enable Annotation Processing**: For code generation tools
2. **Use Project-Specific Code Style**: Import the Google Java style configuration
3. **Configure Checkstyle Plugin**: To ensure code style compliance

## Build and Test

Build and test the project regularly during development:

```bash
# Build the project
./gradlew build

# Run tests
./gradlew test

# Run a specific test
./gradlew test --tests "fully.qualified.TestClassName"

# Run checkstyle validation
./gradlew checkstyleMain checkstyleTest

# Clean build
./gradlew clean build

# Generate JaCoCo test coverage report
./gradlew jacocoTestReport

# Verify test coverage meets thresholds
./gradlew jacocoTestCoverageVerification
```

## Coding Standards

JavaFlow follows the Google Java Style Guide with some specific customizations:

- **Indentation**: 2 spaces (no tabs)
- **Line Length**: Maximum 100 characters
- **Method Length**: Maximum 50 lines preferred
- **Comments**: Javadoc for all public methods and classes
- **Naming Conventions**: 
  - Classes: `PascalCase`
  - Methods/Variables: `camelCase`
  - Constants: `UPPER_SNAKE_CASE`
- **Imports**: No wildcard imports, organized alphabetically
- **Try/Catch blocks**: Always include appropriate error handling
- **Test Naming**: `[MethodName]Test` naming pattern for clarity

Adherence to these standards is enforced via Checkstyle.

### Additional Guidelines

1. **Immutability**: Prefer immutable objects when possible
2. **Error Handling**: Use checked exceptions for API contract errors, unchecked for programming errors
3. **Thread Safety**: All code must be thread-safe or clearly documented if not
4. **Performance**: Be mindful of memory allocations and potential bottlenecks
5. **Documentation**: Keep documentation up-to-date with code changes

## Pull Request Process

1. **Branch Naming**: Use a descriptive branch name: `feature/description`, `bugfix/issue-number`, `docs/topic`, etc.
2. **Keep PRs Focused**: Each PR should address a single concern
3. **Include Tests**: All new features or bug fixes should include tests
4. **Update Documentation**: Update relevant documentation
5. **Create Pull Request**: Submit a PR against the `main` branch
6. **PR Description**: Describe what changes were made and why
7. **Review Process**: Address review comments and update PR as needed
8. **CI Checks**: Ensure all CI checks pass
9. **Approval & Merge**: A core contributor will merge your PR when approved

## Commit Message Guidelines

JavaFlow follows the [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/) specification:

```
<type>(<scope>): <subject>

<body>

<footer>
```

### Types
- **feat**: A new feature
- **fix**: A bug fix
- **docs**: Documentation changes
- **style**: Formatting changes (whitespace, semicolons, etc.)
- **refactor**: Code changes that neither fix bugs nor add features
- **perf**: Performance improvements
- **test**: Adding or updating tests
- **chore**: Maintenance tasks, build changes, etc.

### Examples
```
feat(scheduler): add support for priority aging in task scheduling

Implement the priority aging algorithm to prevent task starvation.
Tasks that wait too long at lower priorities will gradually increase
in priority until they execute.

Closes #123
```

```
fix(core): properly propagate cancellation in FlowFuture chain

When a FlowFuture is cancelled, ensure that all dependent futures
in the chain also receive cancellation signals.

Fixes #456
```

## Issue Reporting Guidelines

When creating an issue, please use the provided templates and include:

1. **Bug Reports**:
   - Version of JavaFlow
   - JDK version and platform
   - Clear steps to reproduce
   - Expected vs. actual behavior
   - Code samples or test cases

2. **Feature Requests**:
   - Clear description of the feature
   - Rationale and use cases
   - Potential implementation approach if you have one

## Documentation

Documentation contributions are highly valued:

1. **Code Comments**: Ensure code is well-commented with Javadoc
2. **README Updates**: Keep README and other docs updated
3. **Design Documentation**: Update design docs when architecture changes
4. **Examples and Tutorials**: Contribute examples showing how to use features

## License

By contributing to JavaFlow, you agree that your contributions will be licensed under the project's [Apache License 2.0](LICENSE).

---

Thank you for contributing to JavaFlow! Your efforts help make this project better for everyone.