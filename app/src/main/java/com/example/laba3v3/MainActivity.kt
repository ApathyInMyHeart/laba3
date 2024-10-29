package com.example.laba3v3

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.net.URL
import java.text.DecimalFormat
import java.text.NumberFormat

class MainActivity : AppCompatActivity() {

    // Переменные для интерфейсных элементов
    private lateinit var inputAmount: EditText
    private lateinit var spinnerFrom: Spinner
    private lateinit var spinnerTo: Spinner
    private lateinit var resultText: TextView
    private lateinit var convertButton: Button

    // Карта для хранения курсов валют
    private val currencies = mutableMapOf<String, Double>()
    // Список для хранения сокращенных названий валют
    private val currencyNames = mutableListOf<String>()
    // Список для хранения названий валют с полными именами
    private val currencyNamesWithFullName = mutableListOf<String>()
    // Карта для хранения полного имени валюты по коду
    private val currencyFullNameMap = mutableMapOf<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Инициализация элементов интерфейса
        inputAmount = findViewById(R.id.inputAmount)
        spinnerFrom = findViewById(R.id.spinnerFrom)
        spinnerTo = findViewById(R.id.spinnerTo)
        resultText = findViewById(R.id.resultText)
        convertButton = findViewById(R.id.convertButton)

        // Запуск корутины для загрузки валют и настройки интерфейса
        lifecycleScope.launch {
            fetchCurrencies() // Загрузка курсов валют с сайта ЦБ РФ
            setupSpinners()   // Настройка выпадающих списков валют

            // Установка слушателя на кнопку конвертации
            convertButton.setOnClickListener {
                convertCurrency() // Выполнение конвертации валют
            }
        }
    }

    // Метод для загрузки курсов валют в фоновом потоке
    private suspend fun fetchCurrencies() {
        withContext(Dispatchers.IO) {
            try {
                // Создание URL для получения XML с курсами валют
                val url = URL("https://www.cbr.ru/scripts/XML_daily.asp")
                // Инициализация XML-парсера
                val factory = XmlPullParserFactory.newInstance()
                factory.isNamespaceAware = false
                val xpp = factory.newPullParser()
                xpp.setInput(url.openStream(), null)

                // Чтение XML-документа
                var eventType = xpp.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG && xpp.name == "Valute") {
                        // Вызов метода для разбора каждого элемента <Valute>
                        parseCurrency(xpp)
                    }
                    eventType = xpp.next()
                }

            } catch (e: Exception) {
                // Обработка ошибок при загрузке данных
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Ошибка загрузки данных: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Метод для разбора элемента <Valute> из XML и сохранения данных о валюте
    private fun parseCurrency(xpp: XmlPullParser) {
        var nominal = 1.0 // Номинал валюты
        var value: Double? = null // Курс валюты
        var charCode: String? = null // Код валюты (например, USD)
        var name: String? = null // Название валюты

        // Чтение данных внутри тега <Valute>
        var eventType = xpp.next()
        while (eventType != XmlPullParser.END_TAG || xpp.name != "Valute") {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    // Чтение значений тегов <Nominal>, <Value>, <CharCode>, <Name>
                    when (xpp.name) {
                        "Nominal" -> nominal = xpp.nextText().replace(",", ".").toDouble()
                        "Value" -> value = xpp.nextText().replace(",", ".").toDouble()
                        "CharCode" -> charCode = xpp.nextText()
                        "Name" -> name = xpp.nextText()
                    }
                }
            }
            eventType = xpp.next() // Переход к следующему элементу XML
        }

        // Если данные валидны, сохраняем их в карты и списки
        if (value != null && charCode != null && name != null) {
            currencies[charCode] = value / nominal // Сохраняем курс валюты с учетом номинала

            // Создаем строку с полным именем валюты и добавляем её в список
            val fullName = "$charCode ($name)"
            currencyFullNameMap[charCode] = name
            currencyNamesWithFullName.add(fullName)
        }
    }

    // Метод для настройки выпадающих списков с валютами
    private fun setupSpinners() {
        // Добавляем рубль как валюту по умолчанию
        currencyNamesWithFullName.add(0,"RUB (Российский рубль)") // Добавляем рубли в список
        currencies["RUB"] = 1.0 // Курс рубля к рублю равен 1
        currencyFullNameMap["RUB"] = "Российский рубль" // Полное имя для рубля

        // Создаем адаптер для выпадающих списков, используя список валют
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, currencyNamesWithFullName)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerFrom.adapter = adapter // Назначаем адаптер спиннеру для исходной валюты
        spinnerTo.adapter = adapter   // Назначаем адаптер спиннеру для конечной валюты
    }

    // Метод для выполнения конвертации валют
    private fun convertCurrency() {
        // Проверка, введена ли сумма для конвертации
        if (inputAmount.text.isEmpty()) {
            Toast.makeText(this, "Введите сумму", Toast.LENGTH_SHORT).show()
            return
        }

        // Преобразование введённой суммы в число
        val amount = inputAmount.text.toString().toDoubleOrNull()
        if (amount == null) {
            Toast.makeText(this, "Неверный формат суммы", Toast.LENGTH_SHORT).show()
            return
        }

        // Получаем коды выбранных валют из выпадающих списков
        val fromCurrencyCode = spinnerFrom.selectedItem.toString().substringBefore(" ") // Код исходной валюты
        val toCurrencyCode = spinnerTo.selectedItem.toString().substringBefore(" ")    // Код конечной валюты

        // Получаем курсы выбранных валют из карты currencies
        val fromRate = currencies[fromCurrencyCode]
        val toRate = currencies[toCurrencyCode]

        // Если курсы валют найдены, выполняем конвертацию
        if (fromRate != null && toRate != null) {
            // Вычисляем результат конвертации
            val result = amount * fromRate / toRate

            // Форматируем результат до двух знаков после запятой
            val numberFormat: NumberFormat = DecimalFormat("#0.00")
            resultText.text = numberFormat.format(result) // Отображаем результат в TextView
        } else {
            // Обработка ошибки, если курс какой-либо валюты не найден
            Toast.makeText(this, "Ошибка конвертации", Toast.LENGTH_SHORT).show()
        }
    }
}
