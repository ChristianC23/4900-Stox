package com.stox.data;

import android.os.Process;
import android.util.Log;

import org.patriques.AlphaVantageConnector;
import org.patriques.TimeSeries;
import org.patriques.input.timeseries.Interval;
import org.patriques.input.timeseries.OutputSize;
import org.patriques.output.AlphaVantageException;
import org.patriques.output.timeseries.IntraDay;
import org.patriques.output.timeseries.data.StockData;

import java.util.List;

public class TickFetcher {

    // singleton instance
    private static final TickFetcher instance = new TickFetcher();

    private TickFetcher() {
    }

    public static TickFetcher getInstance() {
        return instance;
    }

    /**
     * Returns the list of data points for the given stock symbol and interval.
     * @param stockSymbol symbol (AMZN, MSFT, etc.)
     * @param interval interval (ONE_MIN, FIFTEEN_MIN, etc.)
     * @return list of data points
     */
    public List<StockData> getData(final String stockSymbol, final Interval interval) {
        final FetchRunnable fetchRunnable = new FetchRunnable(stockSymbol, interval);
        final Thread thread = new Thread(fetchRunnable);
        thread.run();
        try {
            thread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return fetchRunnable.stockData;
    }

    /**
     * A job class for the actual data fetch, so that we can run it on a background thread.
     */
    private static class FetchRunnable implements Runnable {
        String stockSymbol;
        Interval interval;
        List<StockData> stockData;

        public FetchRunnable(final String stockSymbol, final Interval interval) {
            this.stockSymbol = stockSymbol;
            this.interval = interval;
        }

        @Override
        public void run() {
            // send this thread to the background
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            // connect to AlphaVantage with our API key
            final AlphaVantageConnector connector = new AlphaVantageConnector("1NDS61W4M4GFPFJA", 3000);
            // get and return data
            final TimeSeries stockTimeSeries = new TimeSeries(connector);
            try {
                final IntraDay response = stockTimeSeries.intraDay(stockSymbol, interval, OutputSize.FULL);
                stockData = response.getStockData();
            } catch (final AlphaVantageException e) {
                Log.e("FETCHER", "exception while fetching tick data", e);
            }
        }
    }

}
