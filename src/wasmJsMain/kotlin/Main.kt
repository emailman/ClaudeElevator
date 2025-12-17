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

    // Start with doors open at floor 1
    var doorState by mutableStateOf(DoorState.OPEN)

    // 0 = closed, 1 = open
    var doorProgress by mutableStateOf(1f)

    // 0-1 progress between floors
    var positionProgress by mutableStateOf(0f)
    var isMoving by mutableStateOf(false)
    var queuedFloors by mutableStateOf(setOf<Int>())

    // External call buttons (directional)
    var callButtonsUp by mutableStateOf(setOf<Int>())    // Floors 1-5
    var callButtonsDown by mutableStateOf(setOf<Int>())  // Floors 2-6
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
    LaunchedEffect(
        elevatorState.queuedFloors,
        elevatorState.callButtonsUp,
        elevatorState.callButtonsDown,
        elevatorState.isMoving,
        elevatorState.doorState) {
        // Don't process while doors are closing
        if (elevatorState.doorState == DoorState.CLOSING) {
            return@LaunchedEffect
        }

        if (elevatorState.isMoving) {
            return@LaunchedEffect
        }

        // Check if any requests are pending
        val hasRequests = elevatorState.queuedFloors.isNotEmpty() ||
                elevatorState.callButtonsUp.isNotEmpty() ||
                elevatorState.callButtonsDown.isNotEmpty()

        // Handle button press while idle with doors open
        if (elevatorState.doorState == DoorState.OPEN && hasRequests) {
            elevatorState.doorState = DoorState.CLOSING
            return@LaunchedEffect
        }

        if (elevatorState.doorState == DoorState.CLOSED) {
            // Check if there's a call or queued request at the current floor
            val currentFloor = elevatorState.currentFloor
            val callAtCurrentFloor = currentFloor in elevatorState.callButtonsUp ||
                    currentFloor in elevatorState.callButtonsDown ||
                    currentFloor in elevatorState.queuedFloors

            if (callAtCurrentFloor) {
                // Clear the call buttons/queue and open doors
                elevatorState.callButtonsUp -= currentFloor
                elevatorState.callButtonsDown -= currentFloor
                elevatorState.queuedFloors -= currentFloor
                elevatorState.doorState = DoorState.OPENING
                return@LaunchedEffect
            }

            // Determine next floor using elevator algorithm
            val nextFloor = getNextFloor(elevatorState)
            if (nextFloor != null && nextFloor != currentFloor) {
                elevatorState.targetFloor = nextFloor
                elevatorState.direction =
                    if (nextFloor > currentFloor) Direction.UP
                    else Direction.DOWN
                elevatorState.isMoving = true
            } else if (!hasRequests) {
                // No requests - stay idle at current floor
                elevatorState.direction = Direction.NONE
            }
        }
    }

    // Idle homing logic - return to floor 1 after 5 seconds of no requests
    LaunchedEffect(
        elevatorState.queuedFloors,
        elevatorState.callButtonsUp,
        elevatorState.callButtonsDown,
        elevatorState.isMoving,
        elevatorState.currentFloor
    ) {
        val hasRequests = elevatorState.queuedFloors.isNotEmpty() ||
                elevatorState.callButtonsUp.isNotEmpty() ||
                elevatorState.callButtonsDown.isNotEmpty()

        // Only start homing timer if idle, not moving, and not at floor 1
        if (!hasRequests && !elevatorState.isMoving &&
            elevatorState.currentFloor != 1) {
            // Wait 5 seconds
            delay(5000)

            // Re-check conditions after delay
            val stillNoRequests = elevatorState.queuedFloors.isEmpty() &&
                    elevatorState.callButtonsUp.isEmpty() &&
                    elevatorState.callButtonsDown.isEmpty()

            if (stillNoRequests && !elevatorState.isMoving &&
                elevatorState.currentFloor != 1) {
                // Home to floor 1 with doors open
                elevatorState.targetFloor = 1
                elevatorState.direction = Direction.DOWN
                elevatorState.isMoving = true
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
                    elevatorState.doorProgress =
                        startProgress + (1f - startProgress) * eased
                    delay(16)
                }
                elevatorState.doorProgress = 1f

                // Dwell time (doors visually open,
                // state still OPENING to prevent cancellation)
                delay(2000)

                // Now decide: close doors or stay open
                val hasRequests = elevatorState.queuedFloors.isNotEmpty() ||
                        elevatorState.callButtonsUp.isNotEmpty() ||
                        elevatorState.callButtonsDown.isNotEmpty()
                if (hasRequests || elevatorState.currentFloor != 1) {
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
                    elevatorState.doorProgress =
                        startProgress * (1f - eased)
                    delay(16)
                }
                elevatorState.doorProgress = 0f
                elevatorState.doorState = DoorState.CLOSED
            }
            else -> {}
        }
    }

    // Movement animation - smooth continuous motion,
    // stops at queued floors and directional call buttons
    LaunchedEffect(
        elevatorState.isMoving,
        elevatorState.targetFloor) {
        if (!elevatorState.isMoving) return@LaunchedEffect

        val startFloor = elevatorState.currentFloor
        val direction =
            if (elevatorState.targetFloor > startFloor) 1 else -1
        val movingUp = direction == 1
        val totalFloors =
            kotlin.math.abs(elevatorState.targetFloor - startFloor)
        val msPerFloor = 2000
        val totalDuration = msPerFloor * totalFloors
        val frames = totalDuration / 16

        var lastPassedFloor = startFloor
        var stopFloor: Int? = null

        for (i in 1..frames) {
            val linearProgress = i.toFloat() / frames

            // Ease in-out for smooth acceleration/deceleration
            val eased = if (linearProgress < 0.5f) {
                2f * linearProgress * linearProgress
            } else {
                1f - (-2f * linearProgress + 2f).let { it * it } / 2f
            }

            val totalFloorProgress = eased * totalFloors
            val currentFloorFromStart = totalFloorProgress.toInt()
            val floorProgress = totalFloorProgress - currentFloorFromStart

            val newFloor = startFloor + (currentFloorFromStart * direction)
            elevatorState.currentFloor = newFloor.coerceIn(1, 6)
            elevatorState.positionProgress = floorProgress

            // Check if we just arrived at a new floor that needs service
            if (newFloor != lastPassedFloor && newFloor != startFloor) {
                // Stop for internal buttons always
                val internalStop = newFloor in elevatorState.queuedFloors
                // Stop for call buttons only if traveling in matching direction
                val callStop = if (movingUp) {
                    newFloor in elevatorState.callButtonsUp
                } else {
                    newFloor in elevatorState.callButtonsDown
                }

                if (internalStop || callStop) {
                    stopFloor = newFloor
                    break
                }
                lastPassedFloor = newFloor
            }

            delay(16)
        }

        // Finalize position at stop floor or target
        val arrivedFloor = stopFloor ?: elevatorState.targetFloor
        val isTargetArrival = stopFloor == null
        elevatorState.currentFloor = arrivedFloor
        elevatorState.positionProgress = 0f

        // Clear internal queue
        if (arrivedFloor in elevatorState.queuedFloors) {
            elevatorState.queuedFloors -= arrivedFloor
        }

        // Clear call buttons - always clear BOTH directions at arrived floor
        // This handles the case where both up and down were pressed at the same floor
        elevatorState.callButtonsUp -= arrivedFloor
        elevatorState.callButtonsDown -= arrivedFloor

        // Clear direction if no more destinations queued
        if (elevatorState.queuedFloors.isEmpty()) {
            elevatorState.direction = Direction.NONE
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
            // Left side: Elevator shaft with call buttons
            ElevatorShaftWithCallButtons(
                elevatorState = elevatorState,
                modifier = Modifier
                    .weight(2.5f)
                    .fillMaxHeight()
                    .padding(16.dp)
            )

            // Right side: Internal elevator button panel
            ElevatorButtonPanel(
                litButtons = elevatorState.queuedFloors,
                onButtonPress = { floor ->
                    // Don't light button if elevator is at that floor with doors open
                    if (elevatorState.currentFloor == floor &&
                        !elevatorState.isMoving &&
                        elevatorState.doorState == DoorState.OPEN) {
                        return@ElevatorButtonPanel
                    }
                    elevatorState.queuedFloors =
                        if (floor in elevatorState.queuedFloors) {
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

// SCAN/Elevator algorithm:
// continue in the current direction, then reverse
// Considers both internal buttons (queuedFloors) and external call buttons (directional)
fun getNextFloor(state: ElevatorState): Int? {
    val allEmpty = state.queuedFloors.isEmpty() &&
            state.callButtonsUp.isEmpty() &&
            state.callButtonsDown.isEmpty()
    if (allEmpty) return null

    val current = state.currentFloor

    // Floors to service when going UP: internal requests + up call buttons
    val upFloors = state.queuedFloors + state.callButtonsUp
    // Floors to service when going DOWN: internal requests + down call buttons
    val downFloors = state.queuedFloors + state.callButtonsDown

    return when (state.direction) {
        Direction.UP -> {
            // Look for floors above that need UP service
            val floorsAbove = upFloors.filter { it > current }.minOrNull()

            // If none above, reverse: get highest floor needing DOWN service
            floorsAbove ?: downFloors.filter { it < current }.maxOrNull()
            // If still none, check for any remaining up calls below (edge case)
            ?: upFloors.filter { it < current }.maxOrNull()
        }
        Direction.DOWN -> {
            // Look for floors below that need DOWN service
            val floorsBelow = downFloors.filter { it < current }.maxOrNull()

            // If none below, reverse: get lowest floor needing UP service
            floorsBelow ?: upFloors.filter { it > current }.minOrNull()
            // If still none, check for any remaining down calls above (edge case)
            ?: downFloors.filter { it > current }.minOrNull()
        }
        Direction.NONE -> {
            // Pick the nearest floor from any queue
            val allFloors = state.queuedFloors + state.callButtonsUp + state.callButtonsDown
            allFloors.minByOrNull { kotlin.math.abs(it - current) }
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
    val floorColor =
        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
    val labelColor =
        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)

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
                        y = floorTop +
                                (floorHeight - textLayoutResult.size.height) / 2
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
            val directionMultiplier =
                if (elevatorState.direction == Direction.UP) 1f else -1f
            val carClearance = floorHeight * 0.15f
            val carHeight = floorHeight - carClearance

            val baseY = size.height - (elevatorFloor * floorHeight)
            val carY = baseY + carClearance -
                    (progress * floorHeight * directionMultiplier)

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

            // Door width shrinks as they open
            // (doorProgress: 0=closed, 1=open)
            val currentDoorWidth =
                maxDoorWidth * (1f - elevatorState.doorProgress)

            // Only draw doors if they have width
            if (currentDoorWidth > 0.5f) {
                // Left door - outer edge fixed,
                // inner edge shrinks away from the center
                drawRect(
                    color = doorColor,
                    topLeft = Offset(centerX - doorGap / 2 - maxDoorWidth, doorY),
                    size = Size(currentDoorWidth, doorHeight)
                )

                // Right door - outer edge fixed, inner edge shrinks away from the center
                drawRect(
                    color = doorColor,
                    topLeft = Offset(centerX + doorGap / 2 +
                            maxDoorWidth - currentDoorWidth, doorY),
                    size = Size(currentDoorWidth, doorHeight)
                )
            }

            // Draw the direction indicator (arrow)
            // if there are calls to service
            if (elevatorState.direction != Direction.NONE) {
                val arrowSize = carHeight * 0.2f
                val arrowCenterY = carY + carHeight * 0.3f

                val path = Path().apply {
                    if (elevatorState.direction == Direction.UP) {
                        moveTo(centerX, arrowCenterY - arrowSize / 2)
                        lineTo(centerX - arrowSize / 2,
                            arrowCenterY + arrowSize / 2)
                        lineTo(centerX + arrowSize / 2,
                            arrowCenterY + arrowSize / 2)
                        close()
                    } else {
                        moveTo(centerX, arrowCenterY + arrowSize / 2)
                        lineTo(centerX - arrowSize / 2,
                            arrowCenterY - arrowSize / 2)
                        lineTo(centerX + arrowSize / 2,
                            arrowCenterY - arrowSize / 2)
                        close()
                    }
                }
                drawPath(path, color = Color.Black.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
fun ElevatorShaftWithCallButtons(
    elevatorState: ElevatorState,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val totalFloors = 6
        // Match the shaft's height calculation (0.9f of available height)
        val shaftHeight = maxHeight * 0.9f
        val floorHeight = shaftHeight / totalFloors
        // Calculate vertical offset to center the shaft area
        val verticalOffset = (maxHeight - shaftHeight) / 2

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Elevator shaft on the left
            ElevatorShaft(
                elevatorState = elevatorState,
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(0.25f)
            )

            // Call buttons column aligned with floors
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(80.dp)
            ) {
                for (floor in 1..totalFloors) {
                    // Calculate Y position to match the floor center
                    // Floors are drawn from bottom (1) to top (6)
                    val floorCenterY = verticalOffset + shaftHeight -
                            (floorHeight * floor) + (floorHeight / 2)

                    Row(
                        modifier = Modifier
                            .offset(y = floorCenterY - 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Up button (floors 1-5 only)
                        if (floor < 6) {
                            CallButton(
                                isUp = true,
                                isLit = floor in elevatorState.callButtonsUp,
                                onClick = {
                                    if (elevatorState.currentFloor == floor &&
                                        !elevatorState.isMoving &&
                                        elevatorState.doorState == DoorState.OPEN) {
                                        return@CallButton
                                    }
                                    if (floor !in elevatorState.callButtonsUp) {
                                        elevatorState.callButtonsUp += floor
                                    }
                                }
                            )
                        } else {
                            Spacer(modifier = Modifier.size(36.dp))
                        }

                        // Down button (floors 2-6 only)
                        if (floor > 1) {
                            CallButton(
                                isUp = false,
                                isLit = floor in elevatorState.callButtonsDown,
                                onClick = {
                                    if (elevatorState.currentFloor == floor &&
                                        !elevatorState.isMoving &&
                                        elevatorState.doorState == DoorState.OPEN) {
                                        return@CallButton
                                    }
                                    if (floor !in elevatorState.callButtonsDown) {
                                        elevatorState.callButtonsDown += floor
                                    }
                                }
                            )
                        } else {
                            Spacer(modifier = Modifier.size(36.dp))
                        }
                    }
                }
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

@Composable
fun CallButton(
    isUp: Boolean,
    isLit: Boolean,
    onClick: () -> Unit
) {
    val buttonColor = if (isLit) {
        Color(0xFFFFB300) // Amber/gold when lit
    } else {
        MaterialTheme.colorScheme.surface
    }

    val arrowColor = if (isLit) {
        Color.Black
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Button(
        onClick = onClick,
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = buttonColor
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = if (isLit) 8.dp else 2.dp
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Canvas(modifier = Modifier.size(18.dp)) {
            val arrowPath = Path().apply {
                if (isUp) {
                    // Up arrow
                    moveTo(size.width / 2, 0f)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                } else {
                    // Down arrow
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width / 2, size.height)
                    close()
                }
            }
            drawPath(arrowPath, color = arrowColor)
        }
    }
}
