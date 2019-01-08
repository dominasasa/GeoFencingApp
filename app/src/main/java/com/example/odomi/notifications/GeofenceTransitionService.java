package com.example.odomi.notifications;

import android.app.IntentService;
import android.content.Intent;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.widget.Toast;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;

import java.util.ArrayList;
import java.util.List;

class GeofenceTransitionService extends IntentService {

    public GeofenceTransitionService(String name)
    {
        super(name);
    }
    @Override
    protected void onHandleIntent(@Nullable Intent intent) {

        GeofencingEvent geofencingEvent = GeofencingEvent.fromIntent(intent);

        if(geofencingEvent.hasError())
        {
            String error = String.valueOf(geofencingEvent.getErrorCode());
            Toast.makeText(getApplicationContext(),"Error:  " + error, Toast.LENGTH_SHORT).show();
            return;
        }

        int geofenceTransition = geofencingEvent.getGeofenceTransition();

        if(geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER | geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT)
        {
            List<Geofence> triggeringGeofence = geofencingEvent.getTriggeringGeofences();
            String geoTransitionDetails = getGeofenceTransitionDetails(geofenceTransition, triggeringGeofence);
        }


    }

    private String getGeofenceTransitionDetails(int geofenceTransition, List<Geofence> triggeringGeofence) {
        ArrayList<String> triggerFenceList = new ArrayList<>();

        for(Geofence geofence: triggeringGeofence)
        {
            triggerFenceList.add(geofence.getRequestId());
        }

        String status = null;

        if(geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER)
        {
            status = "Entering";
        }
        else if( geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT)
        {
            status = "Exiting";
        }

        return status + TextUtils.join(", ", triggerFenceList);
    }
}
