package com.example.odomi.notifications;

import android.Manifest;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Criteria;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.mindorks.placeholderview.PlaceHolderView;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Integer.parseInt;


public class NotifyMe extends FragmentActivity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        com.google.android.gms.location.LocationListener,
        OnMapReadyCallback,
        GoogleMap.OnMapClickListener,
        GoogleMap.OnMarkerClickListener,
        ResultCallback<Status> {

    private GoogleMap mMap;
    AutoCompleteTextView searchView1;
    GoogleApiClient client;

    private Location lastLocation;

    List<Geofence> geofenceList = new ArrayList<Geofence>();
    List<Marker> geofenceMarkerList = new ArrayList<Marker>();


    private PlaceAutocompleteAdapter placeAutocompleteAdapter;
    private static LatLngBounds latLngBounds = new LatLngBounds(new LatLng(-40, -168), new LatLng(71, 136));


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.location_input);

        searchView1 = findViewById(R.id.searchView1);


        SetupMapFragment();

        StartGoogleApi();

        GoogleApiClient dataClient = new GoogleApiClient.Builder(this)
                .addApi(Places.GEO_DATA_API)
                .addApi(Places.PLACE_DETECTION_API)
                .enableAutoManage(this, this)
                .build();


        placeAutocompleteAdapter = new PlaceAutocompleteAdapter(this, dataClient, latLngBounds, null);

        searchView1.setAdapter(placeAutocompleteAdapter);
    }


    @Override
    protected void onStart() {
        super.onStart();

        // Call GoogleApiClient connection when starting the Activity
        client.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();

        for (Geofence geofence : geofenceList) {

            List<String> geofenceID = new ArrayList<String>();
            geofenceID.add(geofence.getRequestId());
            geofenceList.remove(geofence);
            LocationServices.GeofencingApi.removeGeofences(client, geofenceID);

        }

        geofenceMarkerList.clear();
        geofenceList.clear();
        circleList.clear();



        // Disconnect GoogleApiClient when stopping Activity
        client.disconnect();
    }

    // region Permission management

    private final int REQ_PERMISSION = 999;

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQ_PERMISSION: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission granted
                    getLastKnownLocation();

                } else {
                    // Permission denied
                    permissionsDenied();
                }
                break;
            }
        }
    }

    private void permissionsDenied() {

        // TODO close app and warn user
    }


    private void askPermission() {

        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                REQ_PERMISSION
        );
    }

    private boolean checkPermission() {

        return (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED);
    }

    //endregion

    //region SearchButtonSetup

    public void onClick(View v) {

        String g = searchView1.getText().toString();

        Geocoder geocoder = new Geocoder(getBaseContext());
        List<Address> addresses = null;

        try {
            // Getting a maximum of 3 Address that matches the input
            // text
            addresses = geocoder.getFromLocationName(g, 3);
            if (addresses != null && !addresses.equals(""))
                search(addresses);

        } catch (Exception e) {

        }

    }

    protected void search(List<Address> addresses) {

        Address address = (Address) addresses.get(0);
        LatLng latLng = new LatLng(address.getLatitude(), address.getLongitude());

        String addressText = String.format(
                "%s, %s",
                address.getMaxAddressLineIndex() > 0 ? address
                        .getAddressLine(0) : "", address.getCountryName());

        MarkerOptions markerOptions = new MarkerOptions();

        markerOptions.position(latLng);
        markerOptions.title(addressText);

        mMap.clear();
        //mMap.addMarker(markerOptions);
        mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
        mMap.animateCamera(CameraUpdateFactory.zoomTo(15));

    }

    //endregion

    //region GoogleApiClient callbacks

    private LocationRequest locationRequest;

    private void StartGoogleApi() {

        if (client == null) {
            client = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.w("Apppp", "onConnectionSuspended()");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

        Log.w("Apppp", "onConnectionFailed()");
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        getLastKnownLocation();
        //recoverGeofenceMarker();
    }

    private void getLastKnownLocation() {

        if (checkPermission()) {
            lastLocation = LocationServices.FusedLocationApi.getLastLocation(client);
            if (lastLocation != null) {

                //writeLastLocation();
                startLocationUpdates();
            } else {

                startLocationUpdates();
            }
        } else askPermission();
    }

    private void startLocationUpdates() {

        locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(1000 * 10)
                .setFastestInterval(1000 * 5);

        if (checkPermission())
            LocationServices.FusedLocationApi.requestLocationUpdates(client, locationRequest, this);
    }

    //endregion

    //region MapCallbacks
    @Override
    public boolean onMarkerClick(Marker marker) {
        for (Geofence geofence : geofenceList) {
            if (geofence.getRequestId() == marker.getTag().toString()) {
                List<String> geofenceID = new ArrayList<String>();
                geofenceID.add(geofence.getRequestId());
                geofenceList.remove(geofence);
                LocationServices.GeofencingApi.removeGeofences(client, geofenceID);

            }
        }

        for (Circle circle : circleList) {
            if (circle.getTag().toString() == marker.getTag().toString()) {
                circleList.remove(circle);
                circle.remove();
            }

        }
        geofenceMarkerList.remove(marker);
        marker.remove();

        return false;
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        mMap.setMyLocationEnabled(true);
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        Criteria criteria = new Criteria();

        Location location = locationManager.getLastKnownLocation(locationManager.getBestProvider(criteria, false));
        if (location != null) {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(location.getLatitude(), location.getLongitude()), 13));

            CameraPosition cameraPosition = new CameraPosition.Builder()
                    .target(new LatLng(location.getLatitude(), location.getLongitude()))      // Sets the center of the map to location user
                    .zoom(10)                   // Sets the zoom
                    .build();                   // Creates a CameraPosition from the builder
            mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
        }

        mMap.setOnMarkerClickListener(this);
        mMap.setOnMapClickListener(this);
    }

    private void SetupMapFragment() {
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    @Override
    public void onMapClick(LatLng latLng) {

        markerForGeofence(latLng);

    }

    //endregion

    //region NotificationSetup
    private static final String NOTIFICATION_MSG = "NOTIFICATION MSG";

    public static Intent makeNotificationIntent(Context context, String msg) {
        Intent intent = new Intent(context, NotifyMe.class);
        intent.putExtra(NOTIFICATION_MSG, msg);
        return intent;
    }
    //endregion

    //region Geofence setup

    private PendingIntent geoFencePendingIntent;
    private final int GEOFENCE_REQ_CODE = 0;
    private final String KEY_GEOFENCE_LAT = "GEOFENCE LATITUDE";
    private final String KEY_GEOFENCE_LON = "GEOFENCE LONGITUDE";
    Circle geofenceLimit;
    Marker geofenceMarker;
    private String m_Text = "";
    private int m_Int = 0;
    private List<Circle> circleList = new ArrayList<Circle>();

    public void StartGeofencing(View v) {

        LayoutInflater factory = LayoutInflater.from(this);

//text_entry is an Layout XML file containing two text field to display in alert dialog
        final View textEntryView = factory.inflate(R.layout.text_entry, null);

        final EditText input1 = (EditText) textEntryView.findViewById(R.id.EditText1);
        final EditText input2 = (EditText) textEntryView.findViewById(R.id.EditText2);


        input1.setText("FenceID", TextView.BufferType.EDITABLE);
        input2.setText("Radius", TextView.BufferType.EDITABLE);

        final AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle("Enter geofence parameters").setView(textEntryView).setPositiveButton("Save",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,
                                        int whichButton) {
                        m_Text = input1.getText().toString();
                        m_Int = parseInt(input2.getText().toString());


                        if (geofenceMarkerList.get(geofenceMarkerList.size() - 1) != null) {
                            Geofence geofence = createGeofence(m_Text, geofenceMarkerList.get(geofenceMarkerList.size() - 1).getPosition(), m_Int);
                            geofenceList.add(geofence);
                            geofenceMarkerList.get(geofenceMarkerList.size() - 1).setTag(m_Text);
                            GeofencingRequest geofenceRequest = createGeofenceRequest(geofence);
                            addGeofence(geofenceRequest);
                        } else {

                        }

                    }
                }).setNegativeButton("Cancel",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,
                                        int whichButton) {
                        /*
                         * User clicked cancel so do some stuff
                         */
                    }
                });
        alert.show();

    }

    private Geofence createGeofence(String requestID, LatLng latLng, float radius) {

        return new Geofence.Builder()
                .setRequestId(requestID)
                .setCircularRegion(latLng.latitude, latLng.longitude, radius)
                .setExpirationDuration(-1)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER
                        | Geofence.GEOFENCE_TRANSITION_EXIT)
                .build();
    }

    private GeofencingRequest createGeofenceRequest(Geofence geofence) {

        return new GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofence(geofence)
                .build();
    }

    private void addGeofence(GeofencingRequest request) {

        if (checkPermission())
            LocationServices.GeofencingApi.addGeofences(
                    client,
                    request,
                    createGeofencePendingIntent()
            ).setResultCallback(this);
    }

    private PendingIntent createGeofencePendingIntent() {

        if (geoFencePendingIntent != null)
            return geoFencePendingIntent;

        Intent intent = new Intent(this, GeofenceTransitionService.class);
        return PendingIntent.getService(
                this, GEOFENCE_REQ_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    public void onResult(@NonNull Status status) {

        if (status.isSuccess()) {
            saveGeofence();
            drawGeofence();
        } else {
            // inform about fail
        }
    }

    private void saveGeofence() {

        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();

        editor.putLong(KEY_GEOFENCE_LAT, Double.doubleToRawLongBits(geofenceMarkerList.get(geofenceMarkerList.size() - 1).getPosition().latitude));
        editor.putLong(KEY_GEOFENCE_LON, Double.doubleToRawLongBits(geofenceMarkerList.get(geofenceMarkerList.size() - 1).getPosition().longitude));
        editor.apply();
    }

    private void drawGeofence() {

        CircleOptions circleOptions = new CircleOptions()
                .center(geofenceMarkerList.get(geofenceMarkerList.size() - 1).getPosition())
                .strokeColor(Color.argb(50, 70, 70, 70))
                .fillColor(Color.argb(100, 150, 150, 150))
                .radius(m_Int);


        Circle circle = mMap.addCircle(circleOptions);
        circle.setTag(geofenceMarkerList.get(geofenceMarkerList.size() - 1).getTag().toString());

        circleList.add(circle);
    }

    private void markerForGeofence(LatLng latLng) {
        MarkerOptions markerOptions
                = new MarkerOptions().position(latLng).title("Geofence marker");

        if (mMap != null) {

            geofenceMarker = mMap.addMarker(markerOptions);
            geofenceMarkerList.add(geofenceMarker);
        }
    }

    private void recoverGeofenceMarker() {

        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);

        if (sharedPref.contains(KEY_GEOFENCE_LAT) && sharedPref.contains(KEY_GEOFENCE_LON)) {
            double lat = Double.longBitsToDouble(sharedPref.getLong(KEY_GEOFENCE_LAT, -1));
            double lon = Double.longBitsToDouble(sharedPref.getLong(KEY_GEOFENCE_LON, -1));
            LatLng latLng = new LatLng(lat, lon);
            markerForGeofence(latLng);
            drawGeofence();
        }
    }
    //endregion

    @Override
    public void onLocationChanged(Location location) {
        lastLocation = location;
    }


}
