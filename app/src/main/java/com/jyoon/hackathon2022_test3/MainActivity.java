package com.jyoon.hackathon2022_test3;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import net.daum.mf.map.api.MapPOIItem;
import net.daum.mf.map.api.MapPoint;
import net.daum.mf.map.api.MapView;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class MainActivity extends AppCompatActivity implements MapView.MapViewEventListener, MapView.POIItemEventListener {

    public double myLongitude;
    public double myLatitude;

    ViewGroup treeInfoLayout;
    Button btnGetLocation;
    Button btnRename;
    TextView txtTreeName, txtTreeLatitude, txtTreeLongitude, txtTreeAuthor;
    MapView mapView;
    LocationManager locationManager;

    MapPOIItem selectedMarker;
    public int selectedIndex;
    public Intent intent;

    public FirebaseDatabase firebaseDatabase;
    public DatabaseReference databaseReference;

    public ActivityResultLauncher<Intent> launcher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == RESULT_OK)
                    {
                        Intent resultIntent = result.getData();
                        int index = resultIntent.getIntExtra("index", 0);
                        double latitude = intent.getDoubleExtra("latitude", 0);
                        double longitude = intent.getDoubleExtra("longitude", 0);
                        String newName = resultIntent.getStringExtra("newName");
                        String newAuthor = resultIntent.getStringExtra("newAuthor");
                        String s = "[" + index + "] (" + latitude + ", " + longitude + ") (TREE: " + newName + ") (AUTHOR:" + newAuthor + ")";
                        Log.d("??????????????????", s);
                        databaseReference.get().addOnSuccessListener(new OnSuccessListener<DataSnapshot>() {
                            @Override
                            public void onSuccess(DataSnapshot dataSnapshot) {
                                for (DataSnapshot postSnapshot: dataSnapshot.getChildren()) {
                                    int readIndex = Integer.parseInt(postSnapshot.child("id").getValue().toString());
                                    if (index == readIndex) {
                                        User user = new User(Integer.toString(index), newName, newAuthor, Double.toString(longitude), Double.toString(latitude));
                                        Log.d("??????????????????", newName);
                                        Log.d("??????????????????", newAuthor);
                                        Log.d("??????????????????", Double.toString(longitude));
                                        Log.d("??????????????????", Double.toString(latitude));
                                        Log.d("??????????????????", Integer.toString(index));
                                        databaseReference.child(Integer.toString(index))
                                                .setValue(user);

                                        selectedMarker.setItemName(newName);
                                        setTreeInfoLayout(newName, latitude, longitude, newAuthor);
                                    }
                                }
                            }
                        }).addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Log.d("??????????????????", ".");
                            }
                        });
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        firebaseDatabase = FirebaseDatabase.getInstance();
        databaseReference = firebaseDatabase.getReference("tree");
        // Layout fields
        treeInfoLayout = findViewById(R.id.treeInfoLayout);
        btnGetLocation = findViewById(R.id.btnGetLocation);
        btnRename = findViewById(R.id.btnRename);
        txtTreeName = findViewById(R.id.txtTreeName);
        txtTreeLatitude = findViewById(R.id.txtTreeLatitude);
        txtTreeLongitude = findViewById(R.id.txtTreeLongitude);
        txtTreeAuthor = findViewById(R.id.txtAuthor);

        // Location Manager: ?????? ?????? ????????? ???????????? ?????? ??????.
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        // ?????? ?????? ??????(????????? ??????)??? ????????????. myLongitude, myLatitude??? ?????????.
        getLocation(locationManager);

        // ?????? ?????????: ?????? ?????? ??????.
        btnGetLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getLocation(locationManager);
            }
        });
        // ?????? ?????????: ?????? ??????: ?????? ?????? Activity??? ????????????.
        //           TODO:: ????????? ?????? ????????? ????????? ???????????? ????????? name, author??? ???????????? ?????????.
        btnRename.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setIntent();
                //startActivity(intent);
            }
        });

        // TODO:: DEBUGGING
        getHashKey();

        // ?????? ??????.
        mapView = new MapView(this);
        ViewGroup mapViewContainer = (ViewGroup) findViewById(R.id.map_view);
        mapViewContainer.addView(mapView);
        // ?????? ????????? ?????????.
        mapView.setPOIItemEventListener(this);
        // ?????? ????????? ?????????.
        mapView.setMapViewEventListener(this);
        // ?????? ????????? ??? ?????? ??????.
        mapView.setMapCenterPoint(MapPoint.mapPointWithGeoCoord(myLatitude, myLongitude), true);
        // ?????? ????????? ?????? ??????.
        mapMarkerPoint(myLatitude, myLongitude, "?????? ??????", MapPOIItem.MarkerType.BluePin, MapPOIItem.MarkerType.RedPin);
        // 100??? ?????? ?????? ??????.
        //for (int i=0;i<100;i++)temp(myLatitude, myLongitude);

        this.readOnce();
    }

    public void setIntent() {
        databaseReference.get().addOnSuccessListener(new OnSuccessListener<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot dataSnapshot) {
                for (DataSnapshot postSnapshot: dataSnapshot.getChildren()) {
                    int readIndex = Integer.parseInt(postSnapshot.child("id").getValue().toString());
                    if (selectedIndex == readIndex) {

                        String lat = postSnapshot.child("lat").getValue(String.class);
                        String lng = postSnapshot.child("lng").getValue(String.class);
                        String treeName = postSnapshot.child("tree_name").getValue(String.class);
                        String author = postSnapshot.child("user_name").getValue(String.class);
                        String s = "[" + selectedIndex + "] (" + lat + ", " + lng + ") (TREE: " + treeName + ") (AUTHOR:" + author + ")";
                        Log.d("??????????????????", s);
                        if (lat.equals("NULL") || lng.equals("NULL") || lat == "" || lng == "" || lat == null || lng == null
                                || lat.equals("null") || lng.equals("null") || lat.length() == 0 || lng.length() == 0) continue;
                        Double latitude = Double.parseDouble(lat);
                        Double longitude = Double.parseDouble(lng);

                        intent = new Intent(getApplicationContext(), RenameActivity.class);
                        intent.putExtra("index", selectedIndex);
                        intent.putExtra("treeName", txtTreeName.getText());
                        intent.putExtra("treeAuthor", txtTreeAuthor.getText());
                        intent.putExtra("latitude", latitude);
                        intent.putExtra("longitude", longitude);

                        if (intent != null) launcher.launch(intent);
                    }
                }
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.d("??????????????????", ".");
            }
        });
    }
    public void readOnce() {
        databaseReference.get().addOnSuccessListener(new OnSuccessListener<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot dataSnapshot) {
                Log.d("??????????????????", dataSnapshot.toString()); // ??? ??? ??????

                for (DataSnapshot postSnapshot: dataSnapshot.getChildren()) {
                    int index = Integer.parseInt(postSnapshot.child("id").getValue().toString());
                    String lat = postSnapshot.child("lat").getValue(String.class);
                    String lng = postSnapshot.child("lng").getValue(String.class);
                    String treeName = postSnapshot.child("tree_name").getValue(String.class);
                    String author = postSnapshot.child("user_name").getValue(String.class);
                    String s = "[" + index + "] (" + lat + ", " + lng + ") (TREE: " + treeName + ") (AUTHOR:" + author + ")";
                    Log.d("??????????????????", s);
                    if (lat.equals("NULL") || lng.equals("NULL") || lat == "" || lng == "" || lat == null || lng == null
                    || lat.equals("null") || lng.equals("null") || lat.length() == 0 || lng.length() == 0) continue;
                    Double latitude = Double.parseDouble(lat);
                    Double longitude = Double.parseDouble(lng);

                    createTreeMarker(index, latitude, longitude, treeName);
                    //mapMarkerPoint(latitude, longitude, getTreeName(latitude, longitude), MapPOIItem.MarkerType.YellowPin, MapPOIItem.MarkerType.RedPin);

                }


            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.d("??????????????????", ".");
            }
        });
    }









    // getLocation: ??????, ??????, ????????? ????????????.
    private void getLocation(LocationManager locationManager) {
        if ( Build.VERSION.SDK_INT >= 23 &&
                ContextCompat.checkSelfPermission( getApplicationContext(), android.Manifest.permission.ACCESS_FINE_LOCATION ) != PackageManager.PERMISSION_GRANTED ) {
            ActivityCompat.requestPermissions( MainActivity.this, new String[] {
                    android.Manifest.permission.ACCESS_FINE_LOCATION}, 0 );
        }
        else{
            // ?????? ?????? ???????????? ????????????
            Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (location != null) {
                // ?????? ?????? ??????.
                myLongitude = location.getLongitude();
                myLatitude = location.getLatitude();
            }

            // ??????????????? ????????? ??????, ???????????? ???????????????.
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                    1000,
                    1,
                    gpsLocationListener);
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
                    1000,
                    1,
                    gpsLocationListener);
        }
    }

    final LocationListener gpsLocationListener = new LocationListener() {
        public void onLocationChanged(Location location) {
            // LocationListener??? ?????? ????????? ????????? ??? ??????????????? onLocationChanged() ????????? ?????? ?????? ?????? ?????? ????????? ????????????.
            String provider = location.getProvider();  // ????????????
            double longitude = location.getLongitude(); // ??????
            double latitude = location.getLatitude(); // ??????
            double altitude = location.getAltitude(); // ??????
        } public void onStatusChanged(String provider, int status, Bundle extras) {

        } public void onProviderEnabled(String provider) {

        } public void onProviderDisabled(String provider) {

        }
    };

    // mapMarkerPoint: ?????? ????????? ????????? ????????? ?????????.
    private void mapMarkerPoint(double latitude, double longitude, String markerName, MapPOIItem.MarkerType markerType, MapPOIItem.MarkerType markerTypeOnClick) {
        // #1. ?????? ??????.
        //     ????????? ?????? ?????? ????????? ?????? ?????? new MapPoint()??? ????????? ??? ??????, ????????? ???????????? ???.
        MapPoint mapPoint = MapPoint.mapPointWithGeoCoord(latitude, longitude);

        MapPOIItem marker = new MapPOIItem();
        marker.setItemName(markerName);
        marker.setTag(0);
        marker.setMapPoint(mapPoint);
        marker.setMarkerType(markerType);
        marker.setSelectedMarkerType(markerTypeOnClick); // ????????? ???????????????

        mapView.addPOIItem(marker);
    }
    private void createTreeMarker(int id, double lat, double lng, String treeName) {
        if (treeName.length() == 0) treeName = "???????????????";
        MapPoint mapPoint = MapPoint.mapPointWithGeoCoord(lat, lng);
        MapPOIItem marker = new MapPOIItem();
        marker.setItemName(treeName);
        marker.setTag(id);
        marker.setMapPoint(mapPoint);
        if(treeName == "???????????????") {
            marker.setMarkerType(MapPOIItem.MarkerType.CustomImage);
            marker.setCustomImageResourceId(R.drawable.empty);
            marker.setCustomImageAutoscale(false);
            marker.setCustomImageAnchor(0.5f, 1.0f);
        } else {
            marker.setMarkerType(MapPOIItem.MarkerType.CustomImage);
            marker.setCustomImageResourceId(R.drawable.used);
            marker.setCustomImageAutoscale(false);
            marker.setCustomImageAnchor(0.5f, 1.0f);
        }
        mapView.addPOIItem(marker);
    }








    // setTreeInfoLayout:
    private void setTreeInfoLayout(String name, double latitude, double longitude, String author) {
        txtTreeName.setText(name);
        txtTreeLatitude.setText(Double.toString(latitude));
        txtTreeLongitude.setText(Double.toString(longitude));
        txtTreeAuthor.setText(author);
    }
    private String getTreeName(double latitude, double longitude) {
        int a = (int)(latitude * 1000000) - (int)(latitude * 10000) * 100;
        int b = (int)(longitude * 1000000) - (int)(longitude * 10000) * 100;
        return "????????? " + Integer.toString(a) + "_" + Integer.toString(b);
    }
    private String getAuthor(double latitude, double longitude) {
        return getTreeName(latitude, longitude) + "??? ??????";
    }

    private void getHashKey(){
        PackageInfo packageInfo = null;
        try {
            packageInfo = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_SIGNATURES);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        if (packageInfo == null)
            Log.e("KeyHash", "KeyHash:null");

        for (Signature signature : packageInfo.signatures) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA");
                md.update(signature.toByteArray());
                Log.d("KeyHash", Base64.encodeToString(md.digest(), Base64.DEFAULT));
            } catch (NoSuchAlgorithmException e) {
                Log.e("KeyHash", "Unable to get MessageDigest. signature=" + signature, e);
            }
        }
    }

    // ====== MapView.MapViewEventListener ======
    @Override
    public void onMapViewInitialized(MapView mapView) {

    }

    @Override
    public void onMapViewCenterPointMoved(MapView mapView, MapPoint mapPoint) {

    }

    @Override
    public void onMapViewZoomLevelChanged(MapView mapView, int i) {

    }

    @Override
    public void onMapViewSingleTapped(MapView mapView, MapPoint mapPoint) {
        treeInfoLayout.setVisibility(View.GONE);
        selectedIndex = -1;
        selectedMarker = null;
    }

    @Override
    public void onMapViewDoubleTapped(MapView mapView, MapPoint mapPoint) {

    }

    @Override
    public void onMapViewLongPressed(MapView mapView, MapPoint mapPoint) {

    }

    @Override
    public void onMapViewDragStarted(MapView mapView, MapPoint mapPoint) {

    }

    @Override
    public void onMapViewDragEnded(MapView mapView, MapPoint mapPoint) {

    }

    @Override
    public void onMapViewMoveFinished(MapView mapView, MapPoint mapPoint) {

    }
    // ====== MapView.MapViewEventListener ======

    // ====== MapView.POIItemEventListener ======
    @Override
    public void onPOIItemSelected(MapView mapView, MapPOIItem mapPOIItem) {
        selectedMarker = mapPOIItem;
        selectedIndex = mapPOIItem.getTag();
        double latitude = mapPOIItem.getMapPoint().getMapPointGeoCoord().latitude;
        double longitude = mapPOIItem.getMapPoint().getMapPointGeoCoord().longitude;
        treeInfoLayout.setVisibility(View.VISIBLE);




        databaseReference.get().addOnSuccessListener(new OnSuccessListener<DataSnapshot>() {
            @Override
            public void onSuccess(DataSnapshot dataSnapshot) {
                for (DataSnapshot postSnapshot: dataSnapshot.getChildren()) {
                    int readIndex = Integer.parseInt(postSnapshot.child("id").getValue().toString());
                    if (selectedIndex == readIndex) {
                        String author = postSnapshot.child("user_name").getValue(String.class);

                        setTreeInfoLayout(selectedMarker.getItemName(), latitude, longitude, author);

                    }
                }
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.d("??????????????????", ".");
            }
        });
    }

    @Override
    public void onCalloutBalloonOfPOIItemTouched(MapView mapView, MapPOIItem mapPOIItem) {

    }

    @Override
    public void onCalloutBalloonOfPOIItemTouched(MapView mapView, MapPOIItem mapPOIItem, MapPOIItem.CalloutBalloonButtonType calloutBalloonButtonType) {

    }

    @Override
    public void onDraggablePOIItemMoved(MapView mapView, MapPOIItem mapPOIItem, MapPoint mapPoint) {

    }
    // ====== MapView.POIItemEventListener ======
}