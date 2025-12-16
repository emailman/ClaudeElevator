# Elevator Simulator

A visual elevator simulation built with Kotlin/WASM and Compose Multiplatform. The application renders an interactive elevator shaft with animated car movement, doors, and a button panel.

## Features

- **Visual Elevator Shaft**: 6-floor shaft with labeled floors displayed as filled rectangles
- **Animated Elevator Car**: Amber/gold car with gray doors that shrink open and grow closed
- **Smooth Animations**: Eased movement (2 seconds per floor) and door animations (0.5 seconds)
- **SCAN Algorithm**: Elevator services floors using the classic elevator algorithm (continues in current direction, then reverses)
- **Interactive Button Panel**: Click floor buttons to queue destinations; buttons light up when selected
- **Direction Indicator**: Arrow displays on the car showing travel direction
- **Auto-Return**: Elevator returns to floor 1 when idle

## Requirements

- JDK 17 or higher
- Gradle 8.x (included via wrapper)

## Build and Run

1. Clone the repository and navigate to the project directory

2. Start the development server:
   ```
   ./gradlew wasmJsBrowserDevelopmentRun
   ```
   On Windows:
   ```
   gradlew.bat wasmJsBrowserDevelopmentRun
   ```

3. Open your browser to http://localhost:8082

## Project Structure

- `src/wasmJsMain/kotlin/Main.kt` - Main application code including elevator logic and UI components
- `src/wasmJsMain/resources/index.html` - HTML entry point
- `build.gradle.kts` - Gradle build configuration

## Technology Stack

- Kotlin/WASM
- Compose Multiplatform
- Material 3 Design
