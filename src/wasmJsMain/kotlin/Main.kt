import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import kotlinx.coroutines.delay

// Elevator direction
enum class Direction { UP, DOWN, NONE }

// Door state
enum class DoorState { CLOSED, OPENING, OPEN, CLOSING }

// Elevator state holder
class ElevatorState {
    var currentFloor by mutableStateOf(1)
    var targetFloor by mutableStateOf(1)
    var direction by mutableStateOf(Direction.NONE)
    var doorState by mutableStateOf(DoorState.OPEN) // Start with doors open at floor 1
    var doorProgress by mutableStateOf(1f) // 0 = closed, 1 = open
    var positionProgress by mutableStateOf(0f) // 0-1 progress between floors
    var isMoving by mutableStateOf(false)
    var queuedFloors by mutableStateOf(setOf<Int>())
}

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(document.body!!) {
        App()
    }
}

@Composable
fun App() {
    val elevatorState = remember { ElevatorState() }

    // Elevator controller logic
    LaunchedEffect(elevatorState.queuedFloors, elevatorState.isMoving, elevatorState.doorState) {
        // Don't process while doors are closing
        if (elevatorState.doorState == DoorState.CLOSING) {
            return@LaunchedEffect
        }

        if (elevatorState.isMoving) {
            return@LaunchedEffect
        }

        // Handle button press while idle at floor 1 with doors open
        if (elevatorState.doorState == DoorState.OPEN && elevatorState.queuedFloors.isNotEmpty()) {
            elevatorState.doorState = DoorState.CLOSING
            return@LaunchedEffect
        }

        if (elevatorState.doorState == DoorState.CLOSED) {
            // Determine next floor using elevator algorithm
            val nextFloor = getNextFloor(elevatorState)
            if (nextFloor != null && nextFloor != elevatorState.currentFloor) {
                elevatorState.targetFloor = nextFloor
                elevatorState.direction = if (nextFloor > elevatorState.currentFloor) Direction.UP else Direction.DOWN
                elevatorState.isMoving = true
            } else if (elevatorState.queuedFloors.isEmpty() && elevatorState.currentFloor != 1) {
                // Return to floor 1 when idle
                elevatorState.targetFloor = 1
                elevatorState.direction = Direction.DOWN
                elevatorState.isMoving = true
            } else if (elevatorState.queuedFloors.isEmpty()) {
                elevatorState.direction = Direction.NONE
            }
        }
    }

    // Door animation and dwell logic
    LaunchedEffect(elevatorState.doorState) {
        when (elevatorState.doorState) {
            DoorState.OPENING -> {
                // Opening animation
                val startProgress = elevatorState.doorProgress
                val duration = 500
                val frames = duration / 16
                for (i in 1..frames) {
                    val progress = i.toFloat() / frames
                    // Ease out
                    val eased = 1f - (1f - progress) * (1f - progress)
                    elevatorState.doorProgress = startProgress + (1f - startProgress) * eased
                    delay(16)
                }
                elevatorState.doorProgress = 1f

                // Dwell time (doors visually open, state still OPENING to prevent cancellation)
                delay(2000)

                // Now decide: close doors or stay open
                if (elevatorState.queuedFloors.isNotEmpty() || elevatorState.currentFloor != 1) {
                    elevatorState.doorState = DoorState.CLOSING
                } else {
                    // Idle at floor 1 - hide direction arrow
                    elevatorState.direction = Direction.NONE
                    elevatorState.doorState = DoorState.OPEN
                }
            }
            DoorState.CLOSING -> {
                val startProgress = elevatorState.doorProgress
                val duration = 500
                val frames = duration / 16
                for (i in 1..frames) {
                    val progress = i.toFloat() / frames
                    // Ease in
                    val eased = progress * progress
                    elevatorState.doorProgress = startProgress * (1f - eased)
                    delay(16)
                }
                elevatorState.doorProgress = 0f
                elevatorState.doorState = DoorState.CLOSED
            }
            else -> {}
        }
    }

    // Movement animation
    LaunchedEffect(elevatorState.isMoving, elevatorState.targetFloor) {
        if (!elevatorState.isMoving) return@LaunchedEffect

        val floorsToTravel = kotlin.math.abs(elevatorState.targetFloor - elevatorState.currentFloor)
        val totalDuration = 2000 * floorsToTravel
        val startFloor = elevatorState.currentFloor
        val frames = totalDuration / 16

        for (i in 1..frames) {
            val linearProgress = i.toFloat() / frames

            // Ease in-out
            val eased = if (linearProgress < 0.5f) {
                2f * linearProgress * linearProgress
            } else {
                1f - (-2f * linearProgress + 2f).let { it * it } / 2f
            }

            val totalFloorProgress = eased * floorsToTravel
            val direction = if (elevatorState.targetFloor > startFloor) 1 else -1
            val newFloor = startFloor + (totalFloorProgress.toInt() * direction)
            val floorProgress = totalFloorProgress - totalFloorProgress.toInt()

            elevatorState.currentFloor = newFloor.coerceIn(1, 6)
            elevatorState.positionProgress = floorProgress
            delay(16)
        }

        elevatorState.currentFloor = elevatorState.targetFloor
        elevatorState.positionProgress = 0f

        // Remove from queue and open doors BEFORE the setting isMoving = false
        if (elevatorState.targetFloor in elevatorState.queuedFloors) {
            elevatorState.queuedFloors -= elevatorState.targetFloor
        }
        elevatorState.doorState = DoorState.OPENING
        elevatorState.isMoving = false
    }

    MaterialTheme(
        colorScheme = darkColorScheme()
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Left side: Elevator shaft visualization
            ElevatorShaft(
                elevatorState = elevatorState,
                modifier = Modifier
                    .weight(2f)
                    .fillMaxHeight()
                    .padding(16.dp)
            )

            // Right side: Elevator button panel
            ElevatorButtonPanel(
                litButtons = elevatorState.queuedFloors,
                onButtonPress = { floor ->
                    // Don't light button if elevator is at that floor
                    if (elevatorState.currentFloor == floor && !elevatorState.isMoving) {
                        return@ElevatorButtonPanel
                    }
                    elevatorState.queuedFloors = if (floor in elevatorState.queuedFloors) {
                        elevatorState.queuedFloors - floor
                    } else {
                        elevatorState.queuedFloors + floor
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
    }
}

// SCAN/Elevator algorithm: continue in the current direction, then reverse
fun getNextFloor(state: ElevatorState): Int? {
    if (state.queuedFloors.isEmpty()) return null

    val current = state.currentFloor
    val queued = state.queuedFloors

    return when (state.direction) {
        Direction.UP -> {
            // Look for the floors above
            val floorsAbove = queued.filter { it > current }.minOrNull()
            floorsAbove ?: // Reverse direction, get the highest floor below
            queued.filter { it < current }.maxOrNull()
        }
        Direction.DOWN -> {
            // Look for floors below
            val floorsBelow = queued.filter { it < current }.maxOrNull()
            floorsBelow ?: // Reverse direction, get the lowest floor above
            queued.filter { it > current }.minOrNull()
        }
        Direction.NONE -> {
            // Pick the nearest floor
            queued.minByOrNull { kotlin.math.abs(it - current) }
        }
    }
}

@Composable
fun ElevatorShaft(
    elevatorState: ElevatorState,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val carColor = Color(0xFFFFB300) // Amber/gold
    val doorColor = Color(0xFF757575) // Gray
    val floorColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
    val labelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxHeight(0.9f)
                .aspectRatio(0.3f) // Tall and narrow
        ) {
            val totalFloors = 6
            val floorHeight = size.height / totalFloors
            val shaftLeft = 40.dp.toPx() // Space for labels
            val shaftWidth = size.width - shaftLeft
            val carWidth = shaftWidth * 0.8f
            val carLeftOffset = shaftLeft + (shaftWidth - carWidth) / 2

            // Draw floor labels and floor rectangles
            val floorGap = 4.dp.toPx() // Gap between floors
            for (floor in 1..totalFloors) {
                val floorTop = size.height - (floor * floorHeight)

                // Floor label
                val labelText = floor.toString()
                val textLayoutResult = textMeasurer.measure(
                    text = labelText,
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = labelColor
                    )
                )
                drawText(
                    textLayoutResult = textLayoutResult,
                    topLeft = Offset(
                        x = (shaftLeft - textLayoutResult.size.width) / 2,
                        y = floorTop + (floorHeight - textLayoutResult.size.height) / 2
                    )
                )

                // Floor rectangle (with a gap at the top)
                drawRect(
                    color = floorColor,
                    topLeft = Offset(shaftLeft, floorTop + floorGap),
                    size = Size(shaftWidth, floorHeight - floorGap)
                )
            }

            // Calculate elevator position
            val elevatorFloor = elevatorState.currentFloor
            val progress = elevatorState.positionProgress
            val directionMultiplier = if (elevatorState.direction == Direction.UP) 1f else -1f
            val carClearance = floorHeight * 0.15f
            val carHeight = floorHeight - carClearance

            val baseY = size.height - (elevatorFloor * floorHeight)
            val carY = baseY + carClearance - (progress * floorHeight * directionMultiplier)

            // Draw elevator car body
            drawRect(
                color = carColor,
                topLeft = Offset(carLeftOffset, carY),
                size = Size(carWidth, carHeight)
            )

            // Draw doors
            val maxDoorWidth = carWidth * 0.35f
            val doorHeight = carHeight * 0.85f
            val doorY = carY + (carHeight - doorHeight) / 2
            val doorGap = carWidth * 0.05f
            val centerX = carLeftOffset + carWidth / 2

            // Door width shrinks as they open (doorProgress: 0=closed, 1=open)
            val currentDoorWidth = maxDoorWidth * (1f - elevatorState.doorProgress)

            // Only draw doors if they have width
            if (currentDoorWidth > 0.5f) {
                // Left door - outer edge fixed, inner edge shrinks away from the center
                drawRect(
                    color = doorColor,
                    topLeft = Offset(centerX - doorGap / 2 - maxDoorWidth, doorY),
                    size = Size(currentDoorWidth, doorHeight)
                )

                // Right door - outer edge fixed, inner edge shrinks away from the center
                drawRect(
                    color = doorColor,
                    topLeft = Offset(centerX + doorGap / 2 + maxDoorWidth - currentDoorWidth, doorY),
                    size = Size(currentDoorWidth, doorHeight)
                )
            }

            // Draw the direction indicator (arrow) if there are calls to service
            if (elevatorState.direction != Direction.NONE) {
                val arrowSize = carHeight * 0.2f
                val arrowCenterY = carY + carHeight * 0.3f

                val path = Path().apply {
                    if (elevatorState.direction == Direction.UP) {
                        moveTo(centerX, arrowCenterY - arrowSize / 2)
                        lineTo(centerX - arrowSize / 2, arrowCenterY + arrowSize / 2)
                        lineTo(centerX + arrowSize / 2, arrowCenterY + arrowSize / 2)
                        close()
                    } else {
                        moveTo(centerX, arrowCenterY + arrowSize / 2)
                        lineTo(centerX - arrowSize / 2, arrowCenterY - arrowSize / 2)
                        lineTo(centerX + arrowSize / 2, arrowCenterY - arrowSize / 2)
                        close()
                    }
                }
                drawPath(path, color = Color.Black.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
fun ElevatorButtonPanel(
    litButtons: Set<Int>,
    onButtonPress: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.padding(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            Text(
                text = "SELECT FLOOR",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Floor buttons 6 down to 1 (top to bottom)
            for (floor in 6 downTo 1) {
                FloorButton(
                    floor = floor,
                    isLit = floor in litButtons,
                    onClick = { onButtonPress(floor) }
                )
                if (floor > 1) {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
fun FloorButton(
    floor: Int,
    isLit: Boolean,
    onClick: () -> Unit
) {
    val buttonColor = if (isLit) {
        Color(0xFFFFB300) // Amber/gold when lit
    } else {
        MaterialTheme.colorScheme.surface
    }

    val textColor = if (isLit) {
        Color.Black
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Button(
        onClick = onClick,
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = buttonColor
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = if (isLit) 8.dp else 2.dp
        )
    ) {
        Text(
            text = floor.toString(),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}
