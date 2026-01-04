<div style="text-align: center;">

# Elevator Simulator
### by Claude and Eric-Version 3.2

</div>

A visual elevator simulation built with Kotlin/WASM and Compose Multiplatform. The application renders an interactive elevator shaft with animated car movement, doors, and a button panel.

## Features

- **Visual Elevator Shaft**: 6-floor shaft with labeled floors displayed as filled rectangles
- **Animated Elevator Car**: Amber/gold car with gray doors that shrink open and grow closed
- **Smooth Animations**: Eased movement (2 seconds per floor) and door animations (0.5 seconds)
- **SCAN Algorithm**: Elevator services floors using the classic elevator algorithm (continues in current direction, then reverses)
- **Internal Button Panel**: Click floor buttons inside the elevator to queue destinations; buttons light up when selected and can be toggled off
- **External Call Buttons**: Up/down call buttons next to each floor of the shaft
  - Floor 1 has only an up button; Floor 6 has only a down button
  - Buttons light up amber when pressed and cannot be canceled
  - Aligned vertically with corresponding floors in the shaft
- **Directional Awareness**: Elevator only stops for call buttons when traveling in the matching direction (up calls answered when going up, down calls when going down)
- **Direction Indicator**: Arrow displays on the car showing the travel direction; clears when no destination is queued
- **Auto-Return**: Elevator returns to floor 1 with doors open after 5 seconds of idle time

## Requirements

- JDK 17 or higher
- Gradle 8.x (included via wrapper)

## Build and Run

1. Clone the repository and navigate to the project directory

2. Start the development server (Windows):
   ```
   .\gradlew wasmJsBrowserDevelopmentRun
   ```
   Or:
   ```
   .\gradlew.bat wasmJsBrowserDevelopmentRun
   ```

3. Open your browser to http://localhost:8088

## Project Structure

- `src/wasmJsMain/kotlin/Main.kt` - Main application code including elevator logic and UI components
- `src/wasmJsMain/resources/index.html` - HTML entry point
- `build.gradle.kts` - Gradle build configuration

## Deploy to Vercel

This project can be deployed to Vercel as a static site. The build output is pre-committed to the repository for easy deployment.

### Option 1: One-Click Deploy

1. Fork this repository to your GitHub account
2. Go to [vercel.com](https://vercel.com) and sign in with GitHub
3. Click "Add New Project"
4. Import your forked repository
5. Vercel will auto-detect the `vercel.json` configuration - just click "Deploy"

### Option 2: Deploy via Vercel CLI

1. Install the Vercel CLI:
   ```
   npm install -g vercel
   ```

2. Navigate to the project directory and run:
   ```
   vercel
   ```

3. Follow the prompts to link or create a new project

### Updating the Deployment

If you make changes to the source code:

1. Build the production bundle:
   ```
   .\gradlew wasmJsBrowserProductionWebpack
   ```

2. Commit the updated build output in `build/dist/wasmJs/productionExecutable`

3. Push to your repository - Vercel will automatically redeploy

### Configuration

The `vercel.json` file configures Vercel to serve static files from the build output directory:
- **Output Directory**: `build/dist/wasmJs/productionExecutable`
- **Build Command**: None (uses pre-built files)
- **Framework**: None (static deployment)

## Technology Stack

- Kotlin/WASM
- Compose Multiplatform
- Material 3 Design
