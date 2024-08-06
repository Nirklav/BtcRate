package com.thirtynineeighty.btcrate

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.thirtynineeighty.btcrate.MainWidget.Companion.ARROW_DOWN
import com.thirtynineeighty.btcrate.MainWidget.Companion.ARROW_UP
import com.thirtynineeighty.btcrate.MainWidget.Companion.BTC_RATE_COMP_STATE
import com.thirtynineeighty.btcrate.MainWidget.Companion.BTC_RATE_LAST_STATE
import com.thirtynineeighty.btcrate.MainWidget.Companion.BTC_SAVE_ACTION
import com.thirtynineeighty.btcrate.MainWidget.Companion.PREFS_NAME
import com.thirtynineeighty.btcrate.MainWidget.Companion.SAME
import java.util.Locale
import java.util.concurrent.TimeUnit


class MainWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {

        val widgetText = context.getString(R.string.price_text)
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId, widgetText)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        val appWidgetManager = AppWidgetManager.getInstance(context)
        val thisAppWidget = ComponentName(context.packageName, MainWidget::class.java.name)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget)

        if (intent.action == BTC_SAVE_ACTION) {

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val last = prefs.getString(BTC_RATE_LAST_STATE, "0")!!

            prefs
                .edit()
                .putString(BTC_RATE_COMP_STATE, last)
                .apply()

            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId, last)
            }
        }

        if (intent.action == BTC_UPDATE_ACTION) {

            val price = intent.getStringExtra(BinanceBtcRateWorker.BTC_RATE_DATA) ?: return
            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId, price)
            }
        }
    }

    override fun onEnabled(context: Context) {
        val request = PeriodicWorkRequest
            .Builder(BinanceBtcRateWorker::class.java, 15, TimeUnit.MINUTES)
            .setInitialDelay(0, TimeUnit.MINUTES)
            .build()

        WorkManager
            .getInstance(context)
            .enqueueUniquePeriodicWork("BTC_RATE_WORKER_TAG", ExistingPeriodicWorkPolicy.REPLACE, request)
    }

    override fun onDisabled(context: Context) {
        WorkManager.getInstance(context).cancelAllWork()
    }

    companion object {
        const val PREFS_NAME: String = "BtcRateApp"
        const val BTC_RATE_LAST_STATE: String = "BTC_RATE_LAST"
        const val BTC_RATE_COMP_STATE: String = "BTC_RATE_COMP"
        const val BTC_UPDATE_ACTION: String = "BTC_UPDATE_ACTION"
        const val BTC_SAVE_ACTION: String = "BTC_SAVE_ACTION"
        const val ARROW_UP: String = "￪"
        const val ARROW_DOWN: String = "￬"
        const val SAME: String = "-"
    }
}

internal fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, price: String) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val prevPrice = prefs.getString(BTC_RATE_COMP_STATE, "0")!!
    val prevPriceDecimal = prevPrice.toBigDecimal()
    val currentPriceDecimal = price.toBigDecimal()

    prefs
        .edit()
        .putString(BTC_RATE_LAST_STATE, price)
        .apply()

    val views = RemoteViews(context.packageName, R.layout.main_widget)
    views.setTextViewText(R.id.btc_price_text, formatPrice(price))

    val intent = Intent(context, MainWidget::class.java)
    intent.action = BTC_SAVE_ACTION
    views.setOnClickPendingIntent(R.id.btc_icon, PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE))

    if (price == prevPrice) {
        views.setTextViewText(R.id.btc_price_direction, SAME)
        views.setTextColor(R.id.btc_price_direction, context.resources.getColor(R.color.same))
    } else if (currentPriceDecimal > prevPriceDecimal) {
        views.setTextViewText(R.id.btc_price_direction, ARROW_UP)
        views.setTextColor(R.id.btc_price_direction, context.resources.getColor(R.color.arrow_up))
    } else {
        views.setTextViewText(R.id.btc_price_direction, ARROW_DOWN)
        views.setTextColor(R.id.btc_price_direction, context.resources.getColor(R.color.arrow_down))
    }

    appWidgetManager.updateAppWidget(appWidgetId, views)
}

internal fun formatPrice(priceStr: String): String {
    try {
        val firstPart = priceStr.split('.')[0]
        val price = firstPart.toInt()

        val thousands = price / 1000
        val hundreds = Math.round((price % 1000) / 10f)

        return String.format(Locale.ENGLISH, "%d.%d", thousands, hundreds)
    } catch (e: Exception) {
        Log.e("WIDGET", String.format("Error on price format:\n%s", e), e)
    }

    return priceStr
}