package com.example.android.sunshine.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

/**
 * A placeholder fragment containing a simple view.
 */
public class ForecastFragment extends Fragment {

    private final String LOG_TAG = ForecastFragment.class.getSimpleName();

    public ArrayAdapter<String> mForecastAdapter;

    public ForecastFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }



    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.forecast_fragment, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_refresh) {
            updateWeather();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Called when the Fragment is visible to the user.  This is generally
     * tied to {@link android.app.Activity#onStart() Activity.onStart} of the containing
     * Activity's lifecycle.
     */
    @Override
    public void onStart() {
        super.onStart();
        updateWeather();
    }

    private void updateWeather() {
        FetchWeatherTask weatherTask = new FetchWeatherTask();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        String location = prefs.getString(getString(R.string.pref_location_key), getString(R.string.pref_location_default));
        weatherTask.execute(location);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        //Initialize of the adapter
        //Context = getActivity(), id of list item layout = R.layout.list_item_forecast, id of textview = R.id.list_forecast_item_textview, list of data = weekForecast
        mForecastAdapter = new ArrayAdapter<String>(
                //The current context
                getActivity(),
                //ID of list item layout
                R.layout.list_item_forecast,
                //ID of textview
                R.id.list_item_forecast_textview,
                //Forecast data
                new ArrayList<String>());

        View rootView = inflater.inflate(R.layout.fragment_main, container, false);

        // Get reference to the listview, and attach this adapter to it
        final ListView list = (ListView) rootView.findViewById(R.id.listview_forecast);
        list.setAdapter(mForecastAdapter);

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String forecast = mForecastAdapter.getItem(position);
                Intent intent = new Intent(getActivity(), DetailActivity.class)
                        .putExtra(Intent.EXTRA_TEXT, forecast);
                startActivity(intent);
            }
        });

        return rootView;
    }

    public class FetchWeatherTask extends AsyncTask<String, Void, String[]> {
        private final String LOG_TAG = FetchWeatherTask.class.getSimpleName();

        private String getReadableDateString(long dateTime) {
            //The api returns a unix timestamp this method converts to a valid date
            Date date = new Date(dateTime*1000);    //Must be converted to a millisecond to be converted to valid date
            SimpleDateFormat format = new SimpleDateFormat("EE, MM, d");
            return format.format(date).toString();

        }

        private String formatHighAndLow(double high, double low) {

            long roundHigh = Math.round(high);
            long roundLow = Math.round(low);
            return Long.toString(roundHigh) + "/" + Long.toString(roundLow);
        }

        private String[] getWeatherDataFromJson(String forecastJsonStr, int numDays) throws JSONException {

            //Names of the JSON objected that need to be extracted
            final String TAG_LIST = "list";
            final String TAG_WEATHER = "weather";
            final String TAG_TEMPERATURE = "temp";
            final String TAG_MIN = "min";
            final String TAG_MAX = "max";
            final String TAG_DATETIME = "dt";
            final String TAG_DESCRIPTION = "main";

            JSONObject forecastJson = new JSONObject(forecastJsonStr);
            JSONArray weatherArray = forecastJson.getJSONArray(TAG_LIST);

            String resultStr[] = new String[numDays];

            for (int i=0; i<weatherArray.length(); i++) {
                //Use of the format "Day, Description, high/low"
                String day, description, highAndLow;

                //Get the Json object representing the day
                JSONObject dayForecast = weatherArray.getJSONObject(i);

                //Convert day/time
                long dateTime = dayForecast.getLong(TAG_DATETIME);
                day = getReadableDateString(dateTime);

                //Get the description from the node "weather"
                JSONObject weatherObject = dayForecast.getJSONArray(TAG_WEATHER).getJSONObject(0);
                description = weatherObject.getString(TAG_DESCRIPTION);

                //Get high and low temperature and format it like high/low
                JSONObject temperatureObject = dayForecast.getJSONObject(TAG_TEMPERATURE);
                double high = temperatureObject.getDouble(TAG_MAX);
                double low = temperatureObject.getDouble(TAG_MIN);
                highAndLow = formatHighAndLow(high, low);

                // Saturday - Rain - 21/14
                resultStr[i] = day + " - " + description + " - " + highAndLow;
            }

            return resultStr;
        }

        @Override
        protected String[] doInBackground(String... params) {

            // --> Implementing the network connection to the Openweathermap API
            HttpURLConnection urlConnection = null;
            BufferedReader reader = null;

            //Will contain the raw JSON response as a string
            String forecastJsonStr = null;

            //String params for the url query
            String format = "json";
            String unit = "metric";
            int numDays = 7;

            try {
                //Construct the URL for the OpenWeatherMap query
                final String FORECAST_BASE_URL = "http://api.openweathermap.org/data/2.5/forecast/daily?";
                final String QUERY_PARAMS = "q";
                final String FORMAT_PARAMS = "mode";
                final String UNIT_PARAMS = "units";
                final String DAYS_PARAMS = "cnt";

                Uri builtUri = Uri.parse(FORECAST_BASE_URL).buildUpon()
                        .appendQueryParameter(QUERY_PARAMS, params[0])
                        .appendQueryParameter(FORMAT_PARAMS, format)
                        .appendQueryParameter(UNIT_PARAMS, unit)
                        .appendQueryParameter(DAYS_PARAMS, Integer.toString(numDays))
                        .build();

                URL url = new URL(builtUri.toString());

                //Create the request to OpenWeatherMap, and Open the connection
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.connect();   // --> Cause NetworkOnMainThreadException !!!

                //Read the input stream into a string
                InputStream inputStream = urlConnection.getInputStream();
                StringBuffer buffer = new StringBuffer();
                if (inputStream == null) {
                    // Nothing to do
                    return null;
                }
                reader = new BufferedReader(new InputStreamReader(inputStream));

                String line;
                while ((line = reader.readLine()) != null) {
                    buffer.append(line + "\n");
                }

                if (buffer.length() == 0) {
                    //Stream was empty
                    return null;
                }

                forecastJsonStr = buffer.toString();

            } catch (IOException e) {
                Log.e(LOG_TAG, "Error: ", e);
                return null;

            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (final IOException e) {
                        Log.e(LOG_TAG, "Error Closing Stream", e);
                    }
                }

            }
            try {
                return getWeatherDataFromJson(forecastJsonStr, numDays);
            } catch(JSONException e) {
                Log.e(LOG_TAG, e.getMessage(), e);
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String[] result) {
            if(result != null) {
                mForecastAdapter.clear();
                for (String dayForecastStr : result) {
                    mForecastAdapter.add(dayForecastStr);
                }
            }
        }

    }
}