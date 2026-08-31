package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SpreadsheetTable
import com.example.excel.ExcelEvaluator

@Composable
fun ExcelScreen(
    spreadsheets: List<SpreadsheetTable>,
    selectedSheet: SpreadsheetTable?,
    selectedCell: Pair<Int, Int>?,
    formulaInput: String,
    onOpenSheet: (SpreadsheetTable) -> Unit,
    onCloseSheet: () -> Unit,
    onSelectCell: (row: Int, col: Int) -> Unit,
    onFormulaInputChange: (String) -> Unit,
    onApplyFormula: () -> Unit,
    onAddRow: (SpreadsheetTable) -> Unit,
    onAddCol: (SpreadsheetTable) -> Unit,
    onCreateNewSheet: (title: String) -> Unit,
    onDeleteSheet: (SpreadsheetTable) -> Unit,
    onExportCsv: (SpreadsheetTable) -> String,
    onImportCsv: (title: String, csvText: String) -> Unit,
    parseGridData: (gridJson: String, rows: Int, cols: Int) -> List<List<String>>,
    parseHeaders: (headersJson: String, cols: Int) -> List<String>
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var showCsvImportDialog by remember { mutableStateOf(false) }
    var csvExportContent by remember { mutableStateOf<String?>(null) }

    if (selectedSheet == null) {
        // Sheet Manager / Selection List
        Scaffold(
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("create_sheet_fab")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "New Spreadsheet")
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Spreadsheets & Formulas (MS Excel)",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Calculations, formulas (=SUM), budgeting and data charts",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row {
                        IconButton(onClick = { showCsvImportDialog = true }) {
                            Icon(imageVector = Icons.Default.Upload, contentDescription = "Import CSV")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (spreadsheets.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.GridOn,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No spreadsheets found! Tap + to create a new spreadsheet.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(spreadsheets, key = { it.id }) { sheet ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenSheet(sheet) }
                                    .testTag("excel_sheet_card_${sheet.id}"),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .background(
                                                    color = MaterialTheme.colorScheme.primaryContainer,
                                                    shape = RoundedCornerShape(10.dp)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.GridOn,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = sheet.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "${sheet.rowsCount} Rows × ${sheet.colsCount} Cols",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                        }
                                    }

                                    IconButton(onClick = { onDeleteSheet(sheet) }) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Sheet",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                        item {
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                }
            }
        }
    } else {
        // Open Active Excel Grid / Charts View
        ActiveExcelSheetEditor(
            sheet = selectedSheet,
            selectedCell = selectedCell,
            formulaInput = formulaInput,
            onClose = onCloseSheet,
            onSelectCell = onSelectCell,
            onFormulaInputChange = onFormulaInputChange,
            onApplyFormula = onApplyFormula,
            onAddRow = { onAddRow(selectedSheet) },
            onAddCol = { onAddCol(selectedSheet) },
            onExportCsv = { csvExportContent = onExportCsv(selectedSheet) },
            parseGridData = parseGridData,
            parseHeaders = parseHeaders
        )
    }

    if (showCreateDialog) {
        CreateSheetDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { title ->
                onCreateNewSheet(title)
                showCreateDialog = false
            }
        )
    }

    if (showCsvImportDialog) {
        CsvImportDialog(
            onDismiss = { showCsvImportDialog = false },
            onImport = { title, csv ->
                onImportCsv(title, csv)
                showCsvImportDialog = false
            }
        )
    }

    csvExportContent?.let { csvText ->
        AlertDialog(
            onDismissRequest = { csvExportContent = null },
            title = { Text("CSV Export") },
            text = {
                OutlinedTextField(
                    value = csvText,
                    onValueChange = {},
                    readOnly = true,
                    minLines = 6,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = { csvExportContent = null }) {
                    Text("Done")
                }
            }
        )
    }
}

@Composable
fun ActiveExcelSheetEditor(
    sheet: SpreadsheetTable,
    selectedCell: Pair<Int, Int>?,
    formulaInput: String,
    onClose: () -> Unit,
    onSelectCell: (row: Int, col: Int) -> Unit,
    onFormulaInputChange: (String) -> Unit,
    onApplyFormula: () -> Unit,
    onAddRow: () -> Unit,
    onAddCol: () -> Unit,
    onExportCsv: () -> Unit,
    parseGridData: (gridJson: String, rows: Int, cols: Int) -> List<List<String>>,
    parseHeaders: (headersJson: String, cols: Int) -> List<String>
) {
    var selectedTabItem by remember { mutableIntStateOf(0) } // 0 = Grid, 1 = Analysis Chart

    val gridData = remember(sheet.gridDataJson, sheet.rowsCount, sheet.colsCount) {
        parseGridData(sheet.gridDataJson, sheet.rowsCount, sheet.colsCount)
    }

    val headers = remember(sheet.headersJson, sheet.colsCount) {
        parseHeaders(sheet.headersJson, sheet.colsCount)
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Header Bar
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onClose) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                        Text(
                            text = sheet.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row {
                        IconButton(onClick = onAddRow) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Add Row")
                        }
                        IconButton(onClick = onExportCsv) {
                            Icon(imageVector = Icons.Default.Download, contentDescription = "Export CSV")
                        }
                    }
                }
            }

            // Tabs
            TabRow(selectedTabIndex = selectedTabItem) {
                Tab(
                    selected = selectedTabItem == 0,
                    onClick = { selectedTabItem = 0 },
                    text = { Text("Grid Editor") },
                    icon = { Icon(imageVector = Icons.Default.GridOn, contentDescription = null) }
                )
                Tab(
                    selected = selectedTabItem == 1,
                    onClick = { selectedTabItem = 1 },
                    text = { Text("Analytics & Chart") },
                    icon = { Icon(imageVector = Icons.Default.BarChart, contentDescription = null) }
                )
            }

            if (selectedTabItem == 0) {
                // Formula Bar
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Functions,
                                contentDescription = "Formula",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))

                            OutlinedTextField(
                                value = formulaInput,
                                onValueChange = onFormulaInputChange,
                                placeholder = { Text("Enter formula e.g. =SUM(C1:C5) or text") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("formula_input_field")
                            )

                            IconButton(onClick = onApplyFormula, modifier = Modifier.testTag("apply_formula_button")) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Apply",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // Formula Quick Shortcuts
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 6.dp)
                        ) {
                            listOf("=SUM(", "=AVG(", "=COUNT(", "=MIN(", "=MAX(").forEach { shortcut ->
                                Button(
                                    onClick = { onFormulaInputChange(shortcut) },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text(shortcut.replace("=", ""), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }

                // 2D Scrollable Excel Grid
                val horizontalScrollState = rememberScrollState()
                val verticalScrollState = rememberScrollState()

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Sticky Row Index Header Column
                        Column(
                            modifier = Modifier
                                .width(40.dp)
                                .fillMaxHeight()
                                .verticalScroll(verticalScrollState)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            // Top Left Blank Cell
                            Box(
                                modifier = Modifier
                                    .size(width = 40.dp, height = 36.dp)
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("#", style = MaterialTheme.typography.labelSmall)
                            }

                            for (r in 0 until sheet.rowsCount) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 40.dp, height = 44.dp)
                                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${r + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Scrollable Main Grid Area
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .horizontalScroll(horizontalScrollState)
                                .verticalScroll(verticalScrollState)
                        ) {
                            // Top Header Row (A, B, C...)
                            Row {
                                for (c in 0 until sheet.colsCount) {
                                    val headerTitle = if (c < headers.size) headers[c] else ExcelEvaluator.getColumnName(c)
                                    Box(
                                        modifier = Modifier
                                            .size(width = 110.dp, height = 36.dp)
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                            .padding(horizontal = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = headerTitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            // Data Rows
                            for (r in 0 until sheet.rowsCount) {
                                Row {
                                    for (c in 0 until sheet.colsCount) {
                                        val isSelected = selectedCell?.first == r && selectedCell.second == c
                                        val rawValue = if (r in gridData.indices && c in gridData[r].indices) gridData[r][c] else ""
                                        val evaluatedValue = ExcelEvaluator.evaluateCell(rawValue, gridData)

                                        Box(
                                            modifier = Modifier
                                                .size(width = 110.dp, height = 44.dp)
                                                .background(
                                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                                    else MaterialTheme.colorScheme.surface
                                                )
                                                .border(
                                                    width = if (isSelected) 2.dp else 1.dp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                                )
                                                .clickable { onSelectCell(r, c) }
                                                .padding(horizontal = 6.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Text(
                                                text = evaluatedValue,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (evaluatedValue.startsWith("#")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Analytics Charts Tab
                ExcelDataAnalyticsView(sheet = sheet, gridData = gridData)
            }
        }
    }
}

@Composable
fun ExcelDataAnalyticsView(
    sheet: SpreadsheetTable,
    gridData: List<List<String>>
) {
    // Collect all evaluated numeric values
    val numbers = remember(gridData) {
        val list = mutableListOf<Double>()
        for (r in gridData) {
            for (c in r) {
                val eval = ExcelEvaluator.evaluateCell(c, gridData)
                eval.toDoubleOrNull()?.let { list.add(it) }
            }
        }
        list
    }

    val sum = numbers.sum()
    val avg = if (numbers.isNotEmpty()) numbers.average() else 0.0
    val maxVal = if (numbers.isNotEmpty()) numbers.maxOrNull() ?: 0.0 else 0.0
    val minVal = if (numbers.isNotEmpty()) numbers.minOrNull() ?: 0.0 else 0.0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Data Summary & Analytics",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Summary Cards Grid
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AnalyticsCard(title = "Total Sum", value = String.format("%.2f", sum), modifier = Modifier.weight(1f))
            AnalyticsCard(title = "Average", value = String.format("%.2f", avg), modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AnalyticsCard(title = "Max Value", value = String.format("%.2f", maxVal), modifier = Modifier.weight(1f))
            AnalyticsCard(title = "Min Value", value = String.format("%.2f", minVal), modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Visual Bar Chart",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (numbers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Enter numeric values in spreadsheet cells to generate chart visualization.")
            }
        } else {
            val chartValues = numbers.take(10) // Display first 10 numerical values
            val maxBar = (chartValues.maxOrNull() ?: 1.0).coerceAtLeast(1.0)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    val barColor = MaterialTheme.colorScheme.primary

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val width = size.width
                        val height = size.height
                        val barWidth = (width / chartValues.size) * 0.6f
                        val gap = (width / chartValues.size) * 0.4f

                        chartValues.forEachIndexed { index, valNum ->
                            val barHeight = ((valNum / maxBar) * height * 0.8f).toFloat()
                            val x = index * (barWidth + gap) + gap / 2
                            val y = height - barHeight

                            drawRect(
                                color = barColor,
                                topLeft = Offset(x, y),
                                size = Size(barWidth, barHeight)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AnalyticsCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun CreateSheetDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var title by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Spreadsheet") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Sheet Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("sheet_title_input")
            )
        },
        confirmButton = {
            Button(
                onClick = { onCreate(title) },
                enabled = title.isNotBlank(),
                modifier = Modifier.testTag("confirm_create_sheet_button")
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun CsvImportDialog(onDismiss: () -> Unit, onImport: (title: String, csv: String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var csvText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import CSV File") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Spreadsheet Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = csvText,
                    onValueChange = { csvText = it },
                    label = { Text("CSV Data (Paste CSV Content)") },
                    minLines = 4,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onImport(title, csvText) },
                enabled = title.isNotBlank() && csvText.isNotBlank()
            ) {
                Text("Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
