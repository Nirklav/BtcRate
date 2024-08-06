package com.thirtynineeighty.btcrate

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL


class BinanceBtcRateWorker(private val context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    override fun doWork(): Result {
        try {
            val url = URL("https://api.binance.com/api/v3/avgPrice?symbol=BTCUSDT")
            val connection = url.openConnection()
            if (connection is HttpURLConnection) {
                if (connection.responseCode == 200) {
                    val reader = InputStreamReader(connection.getInputStream())
                    val json = reader.readText()

                    reader.close()
                    connection.disconnect()

                    val gson = Gson()
                    val btcRate = gson.fromJson(json, BtcRate::class.java)

                    val intent = Intent(context, MainWidget::class.java)
                    intent.action = MainWidget.BTC_UPDATE_ACTION
                    intent.putExtra(BTC_RATE_DATA, btcRate.price)
                    context.sendBroadcast(intent)
                } else {
                    Log.e(TAG, String.format("Response code: %d", connection.responseCode));
                }
            } else {
                Log.e(TAG, String.format("Connection has different type %s", connection::class.toString()));
            }
        }
        catch (e: Exception)
        {
            Log.e(TAG, String.format("Exception happened:\n%s", e), e);
        }


        return Result.success();
    }

    companion object {
        private const val TAG = "BinanceBtcRateWorker"

        const val BTC_RATE_DATA = "BTC_RATE"
    }
}

data class BtcRate(val mins: Int, val price: String, val closeTime: Long)