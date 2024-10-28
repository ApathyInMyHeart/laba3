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

    private lateinit var inputAmount: EditText
    private lateinit var spinnerFrom: Spinner
    private lateinit var spinnerTo: Spinner
    private lateinit var resultText: TextView
    private lateinit var convertButton: Button

    private val currencies = mutableMapOf<String, Double>()
    private val currencyNames = mutableListOf<String>()
    private val currencyNamesWithFullName = mutableListOf<String>() // Новый список для хранения названий с полными именами
    private val currencyFullNameMap = mutableMapOf<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        inputAmount = findViewById(R.id.inputAmount)
        spinnerFrom = findViewById(R.id.spinnerFrom)
        spinnerTo = findViewById(R.id.spinnerTo)
        resultText = findViewById(R.id.resultText)
        convertButton = findViewById(R.id.convertButton)


        lifecycleScope.launch {
            fetchCurrencies()
            setupSpinners()

            convertButton.setOnClickListener {
                convertCurrency()
            }
        }


    }

    private suspend fun fetchCurrencies() {
        withContext(Dispatchers.IO) {
            try {
                val url = URL("https://www.cbr.ru/scripts/XML_daily.asp")
                val factory = XmlPullParserFactory.newInstance()
                factory.isNamespaceAware = false
                val xpp = factory.newPullParser()
                xpp.setInput(url.openStream(), null)

                var eventType = xpp.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG && xpp.name == "Valute") {
                        parseCurrency(xpp)
                    }
                    eventType = xpp.next()
                }

            } catch (e: Exception) {
                // Обработка ошибок, например, вывод сообщения об ошибке
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Ошибка загрузки данных: ${e.message}", Toast.LENGTH_LONG).show()
                }

            }
        }
    }



    private fun parseCurrency(xpp: XmlPullParser) {
        var nominal = 1.0
        var value: Double? = null
        var charCode: String? = null
        var name: String? = null

        var eventType = xpp.next()
        while (eventType != XmlPullParser.END_TAG || xpp.name != "Valute") {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (xpp.name) {
                        "Nominal" -> nominal = xpp.nextText().replace(",", ".").toDouble()
                        "Value" -> value = xpp.nextText().replace(",", ".").toDouble()
                        "CharCode" -> charCode = xpp.nextText()
                        "Name" -> name = xpp.nextText()
                    }
                }
            }

            eventType = xpp.next()

        }
        if (value != null && charCode != null && name != null) {
            currencies[charCode] = value / nominal

            val fullName = "$charCode ($name)" // Создаем строку с полным именем
            currencyFullNameMap[charCode] = name
            currencyNamesWithFullName.add(fullName)  // Добавляем в новый список
        }
    }


    private fun setupSpinners() {
        currencyNamesWithFullName.add(0,"RUB (Российский рубль)") // рубли
        currencies["RUB"] = 1.0 // курс рубля к рублю
        currencyFullNameMap["RUB"] = "Российский рубль" // рубли

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, currencyNamesWithFullName) // Используем новый список
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerFrom.adapter = adapter
        spinnerTo.adapter = adapter
    }

    private fun convertCurrency() {

        if (inputAmount.text.isEmpty()) {
            Toast.makeText(this, "Введите сумму", Toast.LENGTH_SHORT).show()
            return
        }

        val amount = inputAmount.text.toString().toDoubleOrNull()
        if (amount == null ) {
            Toast.makeText(this, "Неверный формат суммы", Toast.LENGTH_SHORT).show()
            return
        }
        val fromCurrencyCode = spinnerFrom.selectedItem.toString().substringBefore(" ") // Получаем код валюты
        val toCurrencyCode = spinnerTo.selectedItem.toString().substringBefore(" ")    // Получаем код валюты
        // Используем fromCurrencyCode и toCurrencyCode для получения курса из currencies

        val fromRate = currencies[fromCurrencyCode]
        val toRate = currencies[toCurrencyCode]

        if (fromRate != null && toRate != null) {

            val result = amount * fromRate / toRate

            val numberFormat: NumberFormat = DecimalFormat("#0.00")


            resultText.text = numberFormat.format(result)
        } else {
            // Обработка ошибки, если курс какой-либо валюты не найден
            Toast.makeText(this, "Ошибка конвертации", Toast.LENGTH_SHORT).show()
        }
    }
}
