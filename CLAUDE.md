# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build and Test Commands

- Build project: `./gradlew build`
- Run tests: `./gradlew test`
- Run a single test: `./gradlew test --tests "fully.qualified.TestClassName"`
- Clean build: `./gradlew clean build`
- Check for lint issues: `./gradlew check`

## Code Style Guidelines

- Follow Google Java Style Guide (block indentation: +2, one statement per line, column limit: 100)
- Use virtual threads for actor implementations (requires JDK 21+)
- Prefer immutability for data structures when possible
- Organize imports alphabetically, no wildcard imports
- Use descriptive naming that follows Java conventions
- Method order: public methods first, then protected, then package-private, then private

## Error Handling

- Prefer checked exceptions for API contract errors
- Use unchecked exceptions for programming errors
- Ensure all futures propagate errors to awaiting actors
- Use proper cancellation propagation for all actor chains

## Documentation

- Document all public API methods with Javadoc
- Include examples in Javadoc for complex or non-obvious APIs
- Write comprehensive documentation for simulation features
- Always read docs/design.md and docs/flow_analysis.md before attempting to write or debug code

## Testing

- Write tests for both normal and simulation mode
- Use deterministic seeds for all simulation tests
- Include fault injection tests for critical components
- Always build/test before committing (ensure passing tests)

## Git Workflow

- Follow conventional commits style (feat, fix, docs, etc.)
- Keep commits focused on single changes
- Write descriptive commit messages that explain the purpose of changes
- Always update README.md before you commit to ensure it is up-to-date