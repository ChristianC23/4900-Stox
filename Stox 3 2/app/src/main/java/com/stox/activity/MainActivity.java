package com.stox.activity;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.StrictMode;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IAxisValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.stox.R;
import com.stox.data.TickFetcher;

import org.patriques.input.timeseries.Interval;
import org.patriques.output.timeseries.data.StockData;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback, TextView.OnEditorActionListener, AdapterView.OnItemSelectedListener {

    private LineChart lineChart;
    private EditText stockSymbolEditText;
    private Spinner favoritesSpinner;
    private TextView dayHighTextView;
    private TextView dayLowTextView;
    private TextView weekHighTextView;
    private TextView weekLowTextView;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);

        // allow any action on the main thread
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        setTitle("Stox");

        setUpTextViews();
        setUpMainChart();
        setUpStockSymbolEditText();
        setUpFavoritesSpinner();
    }

    /**
     * Configure the day and week high and low text views.
     */
    private void setUpTextViews() {
        dayHighTextView = findViewById(R.id.dayHighTextView);
        dayLowTextView = findViewById(R.id.dayLowTextView);
        weekHighTextView = findViewById(R.id.weekHighTextView);
        weekLowTextView = findViewById(R.id.weekLowTextView);
    }

    /**
     * Configure the main chart and its axes.
     */
    private void setUpMainChart() {
        lineChart = findViewById(R.id.mainChart);
        lineChart.getDescription().setTextColor(getResources().getColor(R.color.colorSecondary, null));
        lineChart.getDescription().setEnabled(false);
        lineChart.setTouchEnabled(true);
        lineChart.setDragEnabled(true);
        lineChart.setScaleEnabled(true);
        lineChart.setDrawGridBackground(false);
        lineChart.setPinchZoom(true);
        lineChart.setBackgroundColor(getResources().getColor(R.color.colorPrimary, null));
        lineChart.getLegend().setEnabled(false);

        final LineData data = new LineData();
        data.setValueTextColor(Color.WHITE);

        lineChart.setData(data);

        final XAxis xAxis = lineChart.getXAxis();
        xAxis.setTextColor(getResources().getColor(R.color.colorSecondary, null));
        xAxis.setDrawGridLines(true);
        xAxis.setAvoidFirstLastClipping(true);
        xAxis.setEnabled(true);
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        // we need to convert the x value (seconds since epoch) to a human readable format
        xAxis.setValueFormatter(new IAxisValueFormatter() {
            private final SimpleDateFormat format = new SimpleDateFormat("HH:mm", Locale.US);

            @Override
            public String getFormattedValue(float value, AxisBase axis) {
                final long epochSecond = (long) value;
                final Date date = new Date(epochSecond * 1000);
                return format.format(date);
            }
        });

        final YAxis yAxis = lineChart.getAxisLeft();
        yAxis.setTextColor(getResources().getColor(R.color.colorSecondary, null));
        yAxis.setDrawGridLines(true);

        final YAxis rightAxis = lineChart.getAxisRight();
        rightAxis.setEnabled(false);
    }

    /**
     * Configure the input field for a stock symbol.
     */
    private void setUpStockSymbolEditText() {
        stockSymbolEditText = findViewById(R.id.editText);
        stockSymbolEditText.setOnEditorActionListener(this);
    }

    /**
     * Configure the Spinner widget that holds favorite stocks.
     */
    private void setUpFavoritesSpinner() {
        favoritesSpinner = findViewById(R.id.spinner);

        // retrieve the favorites from the "Stox" shared preferences map
        final Set<String> favoriteStocks = getSharedPreferences("Stox", MODE_PRIVATE).getStringSet("favorites", null);
        // empty by default
        String[] favorites = {};
        // if we have existing preferences, replace the empty array
        if (favoriteStocks != null && !favoriteStocks.isEmpty()) {
            favorites = favoriteStocks.toArray(new String[favoriteStocks.size()]);
        }
        // create a new String adapter to provide data to the Spinner
        final ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                R.layout.favorites_spinner_item,
                favorites
        );
        adapter.setDropDownViewResource(R.layout.favorites_spinner_dropdown_item);
        favoritesSpinner.setAdapter(adapter);
        favoritesSpinner.setOnItemSelectedListener(this);
    }

    /**
     * Populate the chart with the given stock symbol's data.
     * @param stockSymbol the stock symbol (AMZN, MSFT, etc.)
     */
    private void populateChart(final String stockSymbol) {
        // get one minute data for the given stock
        final List<StockData> stockDataList = TickFetcher.getInstance().getData(stockSymbol, Interval.ONE_MIN);
        if (stockDataList == null || stockDataList.isEmpty()) {
            Toast.makeText(getApplicationContext(), "Stock symbol not found.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // sort the data based on timestamps
        Collections.sort(stockDataList, (stockData, t1) -> stockData.getDateTime().compareTo(t1.getDateTime()));

        // create a new dataset for the chart
        final LineData data = lineChart.getData();
        final ILineDataSet dataSet = createDataSet();
        data.removeDataSet(0);
        data.addDataSet(dataSet);

        // get the most recent day in the stock data
        final int dayOfYear = stockDataList.get(stockDataList.size() - 1).getDateTime().getDayOfYear();

        float dayLow = Float.MAX_VALUE;
        float dayHigh = 0.0f;
        float weekLow = Float.MAX_VALUE;
        float weekHigh = 0.0f;

        for (final StockData stockData : stockDataList) {

            final float close = (float) stockData.getClose();

            if (stockData.getDateTime().compareTo(LocalDateTime.now().minusDays(7)) >= 0) {
                if (close < weekLow) {
                    weekLow = close;
                }

                if (close > weekHigh) {
                    weekHigh = close;
                }
            }
            // if the data is from the most recent day
            if (stockData.getDateTime().getDayOfYear() == dayOfYear) {
                // add it to the chart dataset using seconds since epoch (chart only takes numbers, not dates)


                if (close < dayLow) {
                    dayLow = close;
                }

                if (close > dayHigh) {
                    dayHigh = close;
                }

                final LocalDateTime localDateTime = stockData.getDateTime();
                final ZonedDateTime zonedDateTime = localDateTime.atZone(ZoneId.systemDefault());
                final long epochSecond = zonedDateTime.toEpochSecond();
                data.addEntry(new Entry(epochSecond, close), 0);
            }
        }

        dayLowTextView.setText("Day low: " + dayLow);
        dayHighTextView.setText("Day high: " + dayHigh);
        weekLowTextView.setText("Week low: " + weekLow);
        weekHighTextView.setText("Week high: " + weekHigh);

        data.notifyDataChanged();

        // get a human readable string for the date that the data is from
        final Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_YEAR, dayOfYear);
        final String dateString = new SimpleDateFormat("dd MMM yyyy", Locale.US).format(calendar.getTime());

        lineChart.notifyDataSetChanged();
        lineChart.getDescription().setText(stockSymbol + " on " + dateString);
        lineChart.getDescription().setEnabled(true);
        lineChart.invalidate();
    }

    /**
     * Creates and configures a new LineDataSet object for the main chart.
     * @return an empty, but configured LineDataSet
     */
    private LineDataSet createDataSet() {
        final LineDataSet set = new LineDataSet(null, null);
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        set.setColor(ColorTemplate.getHoloBlue());
        set.setCircleColor(Color.WHITE);
        set.setLineWidth(2f);
        set.setCircleRadius(1f);
        set.setFillAlpha(65);
        set.setFillColor(ColorTemplate.getHoloBlue());
        set.setHighLightColor(Color.rgb(244, 117, 117));
        set.setValueTextColor(Color.WHITE);
        set.setValueTextSize(9f);
        set.setDrawValues(false);
        return set;
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.realtime, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.actionFavorite:
                addFavorite();
                break;
            case R.id.actionUnfavorite:
                removeFavorite();
                break;
            case R.id.actionSave:
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    saveToGallery();
                } else {
                    requestStoragePermission(lineChart);
                }
                break;
        }

        return true;
    }

    /**
     * Adds the current stock symbol to the user's favorites
     */
    private void addFavorite() {
        // get the "Stox" shared preference map
        final SharedPreferences sharedPreferences = getSharedPreferences("Stox", MODE_PRIVATE);
        // get the "favorites" set from the "Stox" map
        Set<String> favoriteStocks = sharedPreferences.getStringSet("favorites", null);
        // if it doesn't exist, create a new set
        if (favoriteStocks == null) {
            favoriteStocks = new HashSet<>();
        }

        // and add the current symbol to it
        favoriteStocks.add(stockSymbolEditText.getText().toString());

        // then we reset the adapter that provides data to the spinner
        final String[] favorites = favoriteStocks.toArray(new String[favoriteStocks.size()]);
        final ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                R.layout.favorites_spinner_item,
                favorites
        );
        adapter.setDropDownViewResource(R.layout.favorites_spinner_dropdown_item);
        favoritesSpinner.setAdapter(adapter);

        // and apply the changes to the shared preferences map
        sharedPreferences.edit().putStringSet("favorites", favoriteStocks).apply();
    }

    /**
     * Removes the current stock symbol from the user's favorites.
     */
    private void removeFavorite() {
        // get the "Stox" shared preference map
        final SharedPreferences sharedPreferences = getSharedPreferences("Stox", MODE_PRIVATE);
        // get the "favorites" set from the "Stox" map
        Set<String> favoriteStocks = sharedPreferences.getStringSet("favorites", null);
        // if it exists, and there is a stock currently typed on the screen, and it exists in the set
        if (favoriteStocks != null
                && stockSymbolEditText.getText() != null
                && favoriteStocks.contains(stockSymbolEditText.getText().toString())) {
            // remove it
            favoriteStocks.remove(stockSymbolEditText.getText().toString());
        }

        // then reset the adapter that provides data to the spinner
        final String[] favorites = favoriteStocks.toArray(new String[favoriteStocks.size()]);
        final ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                R.layout.favorites_spinner_item,
                favorites
        );
        adapter.setDropDownViewResource(R.layout.favorites_spinner_dropdown_item);
        favoritesSpinner.setAdapter(adapter);

        // and apply the changes to the shared preferences map
        sharedPreferences.edit().putStringSet("favorites", favoriteStocks).apply();
    }

    /**
     * Saves the current graph to local storage.
     */
    protected void saveToGallery() {
        if (lineChart.saveToGallery("Stox_" + System.currentTimeMillis(), 70))
            Toast.makeText(getApplicationContext(), "Saving SUCCESSFUL!",
                    Toast.LENGTH_SHORT).show();
        else
            Toast.makeText(getApplicationContext(), "Saving FAILED!", Toast.LENGTH_SHORT)
                    .show();
    }

    /**
     * Requests permission from user to store files.
     * @param view
     */
    protected void requestStoragePermission(View view) {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            Snackbar.make(view, "Write permission is required to save image to gallery", Snackbar.LENGTH_INDEFINITE)
                    .setAction(android.R.string.ok, v ->
                            ActivityCompat.requestPermissions(
                                    MainActivity.this,
                                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                    0)
                    ).show();
        } else {
            Toast.makeText(getApplicationContext(), "Permission Required!", Toast.LENGTH_SHORT)
                    .show();
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 0);
        }
    }

    /**********************************************************************************
     * Permission request listener method                                             *
     *********************************************************************************/
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 0) {
            if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                saveToGallery();
            } else {
                Toast.makeText(getApplicationContext(), "Saving FAILED!", Toast.LENGTH_SHORT)
                        .show();
            }
        }
    }

    /**********************************************************************************
     * EditText listener method                                                       *
     *********************************************************************************/
    @Override
    public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
        if ((actionId & EditorInfo.IME_MASK_ACTION) != 0) {
            populateChart(textView.getText().toString());
            return true;
        }

        return false;
    }

    /**********************************************************************************
     * Spinner listener methods                                                       *
     *********************************************************************************/
    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
        final String selectedSymbol = favoritesSpinner.getSelectedItem().toString();
        stockSymbolEditText.setText(selectedSymbol);
        populateChart(selectedSymbol);
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {
        // do nothing
    }
}
