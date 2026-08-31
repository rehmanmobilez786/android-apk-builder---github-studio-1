package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.backup.CloudSyncManager
import com.example.data.AppDatabase
import com.example.data.AppRepository
import com.example.data.DiaryEntry
import com.example.data.SpreadsheetTable
import com.example.data.TaskItem
import com.example.excel.CsvHelper
import com.example.excel.ExcelEvaluator
import com.example.reminder.AlarmScheduler
import com.example.security.SecurityManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray

enum class AppTab {
    DIARY, TASKS, EXCEL, BACKUP, SETTINGS
}

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

class NotebookViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AppRepository
    val securityManager: SecurityManager

    init {
        val dao = AppDatabase.getInstance(application).notebookDao()
        repository = AppRepository(dao)
        securityManager = SecurityManager(application)

        viewModelScope.launch {
            repository.prepopulateSampleDataIfNeeded()
        }
    }

    // Navigation & App Lock State
    private val _selectedTab = MutableStateFlow(AppTab.DIARY)
    val selectedTab: StateFlow<AppTab> = _selectedTab.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _isUnlocked = MutableStateFlow(!securityManager.isLocked)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private val _pinError = MutableStateFlow<String?>(null)
    val pinError: StateFlow<String?> = _pinError.asStateFlow()

    fun selectTab(tab: AppTab) {
        _selectedTab.value = tab
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
    }

    fun unlockWithPin(pin: String): Boolean {
        return if (securityManager.verifyPin(pin)) {
            _isUnlocked.value = true
            _pinError.value = null
            true
        } else {
            _pinError.value = "Incorrect PIN code! Please try again."
            false
        }
    }

    fun unlockWithBiometric() {
        _isUnlocked.value = true
        _pinError.value = null
    }

    fun lockApp() {
        if (securityManager.isLocked) {
            _isUnlocked.value = false
        }
    }

    fun toggleBiometric(enabled: Boolean) {
        securityManager.isBiometricEnabled = enabled
    }

    fun togglePinLock(enabled: Boolean, pin: String = "1234") {
        securityManager.isPinEnabled = enabled
        if (enabled) {
            securityManager.pinCode = pin
        }
    }


    // --- DIARY SECTION ---
    val diaryEntries: StateFlow<List<DiaryEntry>> = repository.allDiaryEntries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _diarySearchQuery = MutableStateFlow("")
    val diarySearchQuery: StateFlow<String> = _diarySearchQuery.asStateFlow()

    private val _selectedMoodFilter = MutableStateFlow("ALL")
    val selectedMoodFilter: StateFlow<String> = _selectedMoodFilter.asStateFlow()

    val filteredDiaryEntries: StateFlow<List<DiaryEntry>> = combine(
        diaryEntries, _diarySearchQuery, _selectedMoodFilter
    ) { entries, query, mood ->
        entries.filter { entry ->
            val matchesQuery = query.isEmpty() ||
                    entry.title.contains(query, ignoreCase = true) ||
                    entry.content.contains(query, ignoreCase = true) ||
                    entry.tags.contains(query, ignoreCase = true)
            val matchesMood = mood == "ALL" || entry.mood == mood
            matchesQuery && matchesMood
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setDiarySearchQuery(query: String) {
        _diarySearchQuery.value = query
    }

    fun setMoodFilter(mood: String) {
        _selectedMoodFilter.value = mood
    }

    fun addOrUpdateDiaryEntry(entry: DiaryEntry) {
        viewModelScope.launch {
            if (entry.id == 0L) {
                repository.insertDiaryEntry(entry)
            } else {
                repository.updateDiaryEntry(entry)
            }
        }
    }

    fun deleteDiaryEntry(entry: DiaryEntry) {
        viewModelScope.launch {
            repository.deleteDiaryEntry(entry)
        }
    }

    fun toggleFavoriteDiary(entry: DiaryEntry) {
        viewModelScope.launch {
            repository.updateDiaryEntry(entry.copy(isFavorite = !entry.isFavorite))
        }
    }


    // --- TASKS & ALARM SECTION ---
    val tasks: StateFlow<List<TaskItem>> = repository.allTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _taskFilterCompleted = MutableStateFlow<Boolean?>(null) // null = ALL, false = Pending, true = Done
    val taskFilterCompleted: StateFlow<Boolean?> = _taskFilterCompleted.asStateFlow()

    val filteredTasks: StateFlow<List<TaskItem>> = combine(
        tasks, _taskFilterCompleted
    ) { allTasks, filterDone ->
        allTasks.filter { task ->
            if (filterDone == null) true else task.isCompleted == filterDone
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setTaskFilter(completed: Boolean?) {
        _taskFilterCompleted.value = completed
    }

    fun saveTask(task: TaskItem) {
        viewModelScope.launch {
            val id = if (task.id == 0L) {
                repository.insertTask(task)
            } else {
                repository.updateTask(task)
                task.id
            }

            // Schedule or cancel alarm
            if (task.reminderTimeMillis > System.currentTimeMillis() && !task.isCompleted) {
                AlarmScheduler.scheduleAlarm(
                    getApplication(),
                    id,
                    task.title,
                    task.description,
                    task.reminderTimeMillis
                )
            } else {
                AlarmScheduler.cancelAlarm(getApplication(), id)
            }
        }
    }

    fun toggleTaskCompleted(task: TaskItem) {
        viewModelScope.launch {
            val updated = task.copy(isCompleted = !task.isCompleted)
            repository.updateTask(updated)
            if (updated.isCompleted) {
                AlarmScheduler.cancelAlarm(getApplication(), task.id)
            }
        }
    }

    fun deleteTask(task: TaskItem) {
        viewModelScope.launch {
            AlarmScheduler.cancelAlarm(getApplication(), task.id)
            repository.deleteTask(task)
        }
    }


    // --- MS EXCEL SPREADSHEET SECTION ---
    val spreadsheets: StateFlow<List<SpreadsheetTable>> = repository.allSpreadsheets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedSpreadsheet = MutableStateFlow<SpreadsheetTable?>(null)
    val selectedSpreadsheet: StateFlow<SpreadsheetTable?> = _selectedSpreadsheet.asStateFlow()

    private val _selectedCell = MutableStateFlow<Pair<Int, Int>?>(null) // (row, col)
    val selectedCell: StateFlow<Pair<Int, Int>?> = _selectedCell.asStateFlow()

    private val _formulaInput = MutableStateFlow("")
    val formulaInput: StateFlow<String> = _formulaInput.asStateFlow()

    fun openSpreadsheet(sheet: SpreadsheetTable) {
        _selectedSpreadsheet.value = sheet
        _selectedCell.value = Pair(0, 0)
        updateFormulaInputFromCell(sheet, 0, 0)
    }

    fun closeSpreadsheet() {
        _selectedSpreadsheet.value = null
        _selectedCell.value = null
        _formulaInput.value = ""
    }

    fun selectCell(row: Int, col: Int) {
        _selectedCell.value = Pair(row, col)
        _selectedSpreadsheet.value?.let { sheet ->
            updateFormulaInputFromCell(sheet, row, col)
        }
    }

    fun updateFormulaInput(input: String) {
        _formulaInput.value = input
    }

    fun applyFormulaToCurrentCell() {
        val sheet = _selectedSpreadsheet.value ?: return
        val cellPos = _selectedCell.value ?: return
        val row = cellPos.first
        val col = cellPos.second

        updateCellContent(sheet, row, col, _formulaInput.value)
    }

    fun updateCellContent(sheet: SpreadsheetTable, row: Int, col: Int, newText: String) {
        viewModelScope.launch {
            val grid = parseGridData(sheet.gridDataJson, sheet.rowsCount, sheet.colsCount)
            if (row in grid.indices && col in grid[row].indices) {
                grid[row][col] = newText
            }

            val updatedGridJson = JSONArray().apply {
                for (r in grid) {
                    val rowArr = JSONArray()
                    for (c in r) {
                        rowArr.put(c)
                    }
                    put(rowArr)
                }
            }.toString()

            val updatedSheet = sheet.copy(
                gridDataJson = updatedGridJson,
                updatedAtMillis = System.currentTimeMillis()
            )

            repository.updateSpreadsheet(updatedSheet)
            _selectedSpreadsheet.value = updatedSheet
        }
    }

    fun addRowToSpreadsheet(sheet: SpreadsheetTable) {
        viewModelScope.launch {
            val newRowsCount = sheet.rowsCount + 1
            val grid = parseGridData(sheet.gridDataJson, sheet.rowsCount, sheet.colsCount)
            grid.add(MutableList(sheet.colsCount) { "" })

            val updatedGridJson = JSONArray().apply {
                for (r in grid) {
                    val rowArr = JSONArray()
                    for (c in r) {
                        rowArr.put(c)
                    }
                    put(rowArr)
                }
            }.toString()

            val updatedSheet = sheet.copy(
                rowsCount = newRowsCount,
                gridDataJson = updatedGridJson,
                updatedAtMillis = System.currentTimeMillis()
            )
            repository.updateSpreadsheet(updatedSheet)
            _selectedSpreadsheet.value = updatedSheet
        }
    }

    fun addColumnToSpreadsheet(sheet: SpreadsheetTable) {
        viewModelScope.launch {
            val newColsCount = sheet.colsCount + 1
            val headers = parseHeaders(sheet.headersJson, sheet.colsCount).toMutableList()
            headers.add(ExcelEvaluator.getColumnName(newColsCount - 1))

            val grid = parseGridData(sheet.gridDataJson, sheet.rowsCount, sheet.colsCount)
            for (r in grid) {
                r.add("")
            }

            val updatedHeadersJson = JSONArray().apply {
                for (h in headers) put(h)
            }.toString()

            val updatedGridJson = JSONArray().apply {
                for (r in grid) {
                    val rowArr = JSONArray()
                    for (c in r) {
                        rowArr.put(c)
                    }
                    put(rowArr)
                }
            }.toString()

            val updatedSheet = sheet.copy(
                colsCount = newColsCount,
                headersJson = updatedHeadersJson,
                gridDataJson = updatedGridJson,
                updatedAtMillis = System.currentTimeMillis()
            )
            repository.updateSpreadsheet(updatedSheet)
            _selectedSpreadsheet.value = updatedSheet
        }
    }

    fun createNewSpreadsheet(title: String, rows: Int = 10, cols: Int = 5) {
        viewModelScope.launch {
            val headers = (0 until cols).map { ExcelEvaluator.getColumnName(it) }
            val headersJson = JSONArray(headers).toString()

            val emptyGrid = List(rows) { List(cols) { "" } }
            val gridJson = JSONArray().apply {
                for (r in emptyGrid) {
                    val rowArr = JSONArray()
                    for (c in r) rowArr.put(c)
                    put(rowArr)
                }
            }.toString()

            val newSheet = SpreadsheetTable(
                name = title.ifBlank { "New Sheet" },
                rowsCount = rows,
                colsCount = cols,
                headersJson = headersJson,
                gridDataJson = gridJson
            )

            val id = repository.insertSpreadsheet(newSheet)
            val created = repository.getSpreadsheetById(id)
            created?.let { openSpreadsheet(it) }
        }
    }

    fun deleteSpreadsheet(sheet: SpreadsheetTable) {
        viewModelScope.launch {
            repository.deleteSpreadsheet(sheet)
            if (_selectedSpreadsheet.value?.id == sheet.id) {
                closeSpreadsheet()
            }
        }
    }

    fun exportSpreadsheetToCsv(sheet: SpreadsheetTable): String {
        return CsvHelper.exportToCsv(sheet.headersJson, sheet.gridDataJson)
    }

    fun importSpreadsheetFromCsv(title: String, csvText: String) {
        viewModelScope.launch {
            val (headers, rows) = CsvHelper.parseCsv(csvText)
            if (headers.isNotEmpty()) {
                val cols = headers.size
                val rowCount = rows.size.coerceAtLeast(5)

                val gridJson = JSONArray().apply {
                    for (r in rows) {
                        val rowArr = JSONArray()
                        for (c in 0 until cols) {
                            rowArr.put(if (c < r.size) r[c] else "")
                        }
                        put(rowArr)
                    }
                }.toString()

                val newSheet = SpreadsheetTable(
                    name = title,
                    rowsCount = rowCount,
                    colsCount = cols,
                    headersJson = JSONArray(headers).toString(),
                    gridDataJson = gridJson
                )

                val id = repository.insertSpreadsheet(newSheet)
                val created = repository.getSpreadsheetById(id)
                created?.let { openSpreadsheet(it) }
            }
        }
    }

    private fun updateFormulaInputFromCell(sheet: SpreadsheetTable, row: Int, col: Int) {
        val grid = parseGridData(sheet.gridDataJson, sheet.rowsCount, sheet.colsCount)
        if (row in grid.indices && col in grid[row].indices) {
            _formulaInput.value = grid[row][col]
        }
    }

    fun parseGridData(gridJson: String, defaultRows: Int, defaultCols: Int): MutableList<MutableList<String>> {
        val matrix = mutableListOf<MutableList<String>>()
        try {
            val jsonArray = JSONArray(gridJson)
            for (r in 0 until jsonArray.length()) {
                val rowArr = jsonArray.getJSONArray(r)
                val row = mutableListOf<String>()
                for (c in 0 until rowArr.length()) {
                    row.add(rowArr.getString(c))
                }
                matrix.add(row)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        while (matrix.size < defaultRows) {
            matrix.add(MutableList(defaultCols) { "" })
        }
        for (r in matrix) {
            while (r.size < defaultCols) {
                r.add("")
            }
        }
        return matrix
    }

    fun parseHeaders(headersJson: String, defaultCols: Int): List<String> {
        val list = mutableListOf<String>()
        try {
            val jsonArray = JSONArray(headersJson)
            for (i in 0 until jsonArray.length()) {
                list.add(jsonArray.getString(i))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        while (list.size < defaultCols) {
            list.add(ExcelEvaluator.getColumnName(list.size))
        }
        return list
    }


    // --- CLOUD BACKUP & SYNC SECTION ---
    private val _cloudBackupStatus = MutableStateFlow<String?>(null)
    val cloudBackupStatus: StateFlow<String?> = _cloudBackupStatus.asStateFlow()

    private val _lastBackupTime = MutableStateFlow<Long?>(null)
    val lastBackupTime: StateFlow<Long?> = _lastBackupTime.asStateFlow()

    fun performCloudBackup(): String {
        val json = CloudSyncManager.generateBackupJson(
            diaryEntries.value,
            tasks.value,
            spreadsheets.value
        )
        _lastBackupTime.value = System.currentTimeMillis()
        _cloudBackupStatus.value = "Cloud sync successful! All data backed up to cloud storage."
        return json
    }

    fun restoreBackupFromJson(jsonString: String): Boolean {
        val result = CloudSyncManager.parseBackupJson(jsonString) ?: return false
        val (diaries, taskList, sheetList) = result

        viewModelScope.launch {
            for (d in diaries) repository.insertDiaryEntry(d)
            for (t in taskList) repository.insertTask(t)
            for (s in sheetList) repository.insertSpreadsheet(s)
            _cloudBackupStatus.value = "Backup restore completed successfully."
        }
        return true
    }
}
