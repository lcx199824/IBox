package com.example.evey.ibox;

import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.baidu.location.BDLocation;
import com.baidu.location.BDLocationListener;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BitmapDescriptor;
import com.baidu.mapapi.map.BitmapDescriptorFactory;
import com.baidu.mapapi.map.InfoWindow;
import com.baidu.mapapi.map.MapStatus;
import com.baidu.mapapi.map.MapStatusUpdate;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.Marker;
import com.baidu.mapapi.map.MarkerOptions;
import com.baidu.mapapi.map.MyLocationConfiguration;
import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.map.OverlayOptions;
import com.baidu.mapapi.map.TextureMapView;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.search.core.PoiInfo;
import com.baidu.mapapi.search.core.RouteLine;
import com.baidu.mapapi.search.poi.OnGetPoiSearchResultListener;
import com.baidu.mapapi.search.poi.PoiDetailResult;
import com.baidu.mapapi.search.poi.PoiIndoorResult;
import com.baidu.mapapi.search.poi.PoiNearbySearchOption;
import com.baidu.mapapi.search.poi.PoiResult;
import com.baidu.mapapi.search.poi.PoiSearch;
import com.baidu.mapapi.search.poi.PoiSortType;
import com.baidu.mapapi.search.route.BikingRouteLine;
import com.baidu.mapapi.search.route.DrivingRouteLine;
import com.baidu.mapapi.search.route.TransitRouteLine;
import com.baidu.mapapi.search.route.WalkingRouteLine;
import com.example.evey.ibox.adapters.ItemMapBottomAdapter;
import com.example.evey.ibox.bean.ServicePointBean;
import com.example.evey.ibox.utils.BitmapUtil;
import com.example.evey.ibox.utils.MapUtils;
import com.example.evey.ibox.utils.ModelUtil;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;

public class MapActivity extends AppCompatActivity
        implements View.OnClickListener, BaiduMap.OnMarkerClickListener, ViewPager.OnPageChangeListener {

    private static final String TAG = "MapActivity";

    @BindView(R.id.viewPager_map)
    ViewPager mViewPager;
    @BindView(R.id.mapView_map)
    TextureMapView mMapView;
    @BindView(R.id.next_map)
    TextView nextStep;
    @BindView(R.id.pre_map)
    TextView preStep;

    private ServicePointBean servicePoint;
    private LinearLayout linearLayout;
    private List<View> bottomPager;
    private BaiduMap mBaiduMap = null;

    //定位组件
    private LocationClient mLocationClient = null;
    private BDLocationListener myListener;
    private BDLocation mLocation;
    private int mCurrentLevel = 15;//当前缩放等级
    private PoiSearch mPoiSearch;
    private MapUtils mapUtils;

    private LatLng startPoi = null;//起始坐标
    private LatLng endPoi = null;//终点坐标
    private boolean isSingleHouse = false; //是否显示单个房子

    //生成路线的方式
    private final static int WAY_WALK = 0;
    private final static int WAY_BUS = 1;
    private final static int WAY_CAR = 2;
    private final static int WAY_BIKE = 3;
    private int WAY = -1;

    private int nodeIndex = -1; // 节点索引,供浏览节点时使用
    private List<ServicePointBean> houseList = new ArrayList<ServicePointBean>();//房子数据

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);
        setTitle("附近的商家");
        mBaiduMap = mMapView.getMap();
        mBaiduMap.setMapType(BaiduMap.MAP_TYPE_NORMAL);//普通地图
        mPoiSearch = PoiSearch.newInstance();//poi搜索
        nextStep.setVisibility(View.GONE);
        preStep.setVisibility(View.GONE);
        initData(savedInstanceState);
        setListener();
    }

    public void initData(Bundle savedInstanceState) {
        houseList = ModelUtil.GetCityData().get(0).getHouseList();
        //定位核心类
        mLocationClient = new LocationClient(getApplicationContext());//声明LocationClient类
        initLocation();//初始化
        mapUtils = MapUtils.getInstance();
        bottomPager = new ArrayList<View>();
        addBottomFragments();
    }

    public void setListener() {
        nextStep.setOnClickListener(this);
        preStep.setOnClickListener(this);
        mViewPager.setOnPageChangeListener(this);
        mViewPager.setCurrentItem(0);

        mPoiSearch.setOnGetPoiSearchResultListener(new OnGetPoiSearchResultListener() {
            @Override
            public void onGetPoiResult(PoiResult poiResult) {
                //获取POI检索结果
                List<PoiInfo> allAddr = poiResult.getAllPoi();
                if (allAddr == null) {
                    Toast.makeText(MapActivity.this, "附近没有搜索到您需要搜索的房子", Toast.LENGTH_LONG).show();
                    return;
                }
                if (allAddr.size() == 1) {
                    Toast.makeText(MapActivity.this, "已定位到该商家", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(MapActivity.this, "为你找到" + allAddr.size() + "家房子", Toast.LENGTH_LONG).show();
                }

                for (PoiInfo p : allAddr) {
                    //定义Maker坐标点
                    LatLng point = new LatLng(p.location.latitude, p.location.longitude);

                    BitmapDescriptor bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(
                            BitmapUtil.BitmapChangeColor(BitmapFactory.decodeResource(getResources(), R.drawable.ic_map_marker_grey600_24dp), Color.RED));

                    //构建MarkerOption，用于在地图上添加Marker
                    OverlayOptions option = new MarkerOptions()
                            .position(point)
                            .icon(bitmapDescriptor);

                    //在地图上添加Marker，并显示
                    mBaiduMap.addOverlay(option);

                    if (isSingleHouse) {
                        endPoi = point;
                        switch (WAY) {
                            case WAY_BIKE:
                                mapUtils.PaintBikingRoute(MapActivity.this, mBaiduMap, startPoi, endPoi);
                                break;
                            case WAY_CAR:
                                mapUtils.PaintDrivingRoute(MapActivity.this, mBaiduMap, startPoi, endPoi);
                                break;
                            case WAY_WALK:
                                mapUtils.PaintWalkingRoute(MapActivity.this, mBaiduMap, startPoi, endPoi);
                                break;
                            case WAY_BUS:
                                mapUtils.PaintTransitRoute(MapActivity.this, mBaiduMap, startPoi, endPoi);
                                break;
                        }
                        //设置导航按钮可见
                        nextStep.setVisibility(View.VISIBLE);
                        preStep.setVisibility(View.VISIBLE);
                    }
                }
            }

            @Override
            public void onGetPoiDetailResult(PoiDetailResult poiDetailResult) {

            }

            @Override
            public void onGetPoiIndoorResult(PoiIndoorResult poiIndoorResult) {

            }
        });

        mBaiduMap.setOnMarkerClickListener(this);
        myListener = new MyLocationListener();
        mLocationClient.registerLocationListener(myListener);//注册监听函数
        mLocationClient.start();
    }



    /**
     * 查找附近的房子
     * @param query
     * @param count
     */
    private void search(String query, int count) {
        if (mLocation == null) {
            if (count == 1) {
                Toast.makeText(MapActivity.this, "正在定位到房子位置..", Toast.LENGTH_LONG).show();
            }
            Toast.makeText(MapActivity.this, "正在尝试为你定位..", Toast.LENGTH_LONG).show();
        }
        mPoiSearch.searchNearby((new PoiNearbySearchOption())
                .location(new LatLng(mLocation.getLatitude(), mLocation.getLongitude()))
                .keyword(query)
                .radius(1000)
                .pageCapacity(count)
                .sortType(PoiSortType.distance_from_near_to_far));
    }

    private void startLocationOverlap() {
        //开启定位图层
        mBaiduMap.setMyLocationEnabled(true);

        //构造定位数据
        MyLocationData locData = new MyLocationData.Builder()
                .accuracy(mLocation.getRadius())
                // 此处设置开发者获取到的方向信息，顺时针0-360
                .direction(0).latitude(mLocation.getLatitude())
                .longitude(mLocation.getLongitude()).build();

        // 设置定位数据
        mBaiduMap.setMyLocationData(locData);

        // 设置定位图层的配置（定位模式，是否允许方向信息，用户自定义定位图标）
        MyLocationConfiguration config = new MyLocationConfiguration(MyLocationConfiguration.LocationMode.NORMAL, true, null);
        mBaiduMap.setMyLocationConfigeration(config);

        //中心点为我的位置
        LatLng cenpt = new LatLng(locData.latitude, locData.longitude);

        //定义地图状态
        MapStatus mMapStatus = new MapStatus.Builder()
                .target(cenpt)
                .zoom(mCurrentLevel)
                .build();

        //定义MapStatusUpdate对象，以便描述地图状态将要发生的变化
        MapStatusUpdate mMapStatusUpdate = MapStatusUpdateFactory.newMapStatus(mMapStatus);

        //改变地图状态
        mBaiduMap.setMapStatus(mMapStatusUpdate);

        // 当不需要定位图层时关闭定位图层
        //mBaiduMap.setMyLocationEnabled(false);
    }

    private void initLocation() {
        LocationClientOption option = new LocationClientOption();
        option.setLocationMode(LocationClientOption.LocationMode.Hight_Accuracy);//可选，默认高精度，设置定位模式，高精度，低功耗，仅设备
        option.setCoorType("bd09ll");//可选，默认gcj02，设置返回的定位结果坐标系
        int span = 2000;
        option.setScanSpan(0);//可选，默认0，即仅定位一次，设置发起定位请求的间隔需要大于等于1000ms才是有效的
        option.setIsNeedAddress(true);//可选，设置是否需要地址信息，默认不需要
        option.setOpenGps(true);//可选，默认false,设置是否使用gps
        option.setLocationNotify(true);//可选，默认false，设置是否当gps有效时按照1S1次频率输出GPS结果
        option.setIsNeedLocationDescribe(true);//可选，默认false，设置是否需要位置语义化结果，可以在BDLocation.getLocationDescribe里得到，结果类似于“在北京天安门附近”
        option.setIsNeedLocationPoiList(true);//可选，默认false，设置是否需要POI结果，可以在BDLocation.getPoiList里得到
        option.setIgnoreKillProcess(false);//可选，默认true，定位SDK内部是一个SERVICE，并放到了独立进程，设置是否在stop的时候杀死这个进程，默认不杀死
        option.SetIgnoreCacheException(false);//可选，默认false，设置是否收集CRASH信息，默认收集
        option.setEnableSimulateGps(false);//可选，默认false，设置是否需要过滤gps仿真结果，默认需要
        mLocationClient.setLocOption(option);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();

        RouteLine route = mapUtils.getNowRoute();

        if (route == null || route.getAllStep() == null) {
            mapUtils.PaintWalkingRoute(MapActivity.this, mBaiduMap, startPoi, endPoi);
            Toast.makeText(MapActivity.this, "自动为您生成步行规划", Toast.LENGTH_LONG).show();
            return;
        }
        if (nodeIndex == -1 && v.getId() == R.id.pre_map) {
            return;
        }

        // 设置节点索引
        if (id == R.id.next_map) {
            if (nodeIndex < route.getAllStep().size() - 1) {
                nodeIndex++;
            } else {
                return;
            }
        } else if (id == R.id.pre_map) {
            if (nodeIndex > 0) {
                nodeIndex--;
            } else {
                return;
            }
        }

        //设置文字导航
        setStep();
    }

    private void setStep() {
        LatLng nodeLocation = null;
        String nodeTitle = null;
        Object step = null;
        RouteLine route = mapUtils.getNowRoute();
        if (route == null) {
            return;
        }

        // 获取节结果信息
        step = route.getAllStep().get(nodeIndex);
        if (step instanceof DrivingRouteLine.DrivingStep) {
            nodeLocation = ((DrivingRouteLine.DrivingStep) step).getEntrance().getLocation();
            nodeTitle = ((DrivingRouteLine.DrivingStep) step).getInstructions();
        } else if (step instanceof WalkingRouteLine.WalkingStep) {
            nodeLocation = ((WalkingRouteLine.WalkingStep) step).getEntrance().getLocation();
            nodeTitle = ((WalkingRouteLine.WalkingStep) step).getInstructions();
        } else if (step instanceof TransitRouteLine.TransitStep) {
            nodeLocation = ((TransitRouteLine.TransitStep) step).getEntrance().getLocation();
            nodeTitle = ((TransitRouteLine.TransitStep) step).getInstructions();
        } else if (step instanceof BikingRouteLine.BikingStep) {
            nodeLocation = ((BikingRouteLine.BikingStep) step).getEntrance().getLocation();
            nodeTitle = ((BikingRouteLine.BikingStep) step).getInstructions();
        }

        if (nodeLocation == null || nodeTitle == null) {
            return;
        }

        // 移动节点至中心
        mBaiduMap.setMapStatus(MapStatusUpdateFactory.newLatLng(nodeLocation));
        // show popup
        TextView popupText = new TextView(MapActivity.this);
        popupText.setBackgroundResource(R.drawable.circle_white_bg);
        popupText.setSingleLine(false);
        popupText.setMaxWidth(500);
        popupText.setTextColor(0xFF000000);
        popupText.setText(nodeTitle);
        popupText.setTextSize(20);
        mBaiduMap.showInfoWindow(new InfoWindow(popupText, nodeLocation, 0));
    }


    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

    }

    @Override
    public void onPageSelected(int position) {
        position = position % houseList.size();

        updateMapMaker(position);
    }

    @Override
    public void onPageScrollStateChanged(int state) {

    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        LatLng position = marker.getPosition();
        int index = 0;
        for (ServicePointBean house : houseList) {
            if (house.getLatitude() == position.latitude && house.getLongitude() == position.longitude) {
                break;
            }
            index++;
        }
        mViewPager.setCurrentItem(index);
        updateMapMaker(index);
        return true;
    }



    private class MyLocationListener implements BDLocationListener {

        @Override
        public void onReceiveLocation(BDLocation location) {
            // map view 销毁后不在处理新接收的位置
            if (location == null || mMapView == null) {
                return;
            }

            mLocation = location;

            updateMapMaker(0);

            startPoi = new LatLng(location.getLatitude(), location.getLongitude());
            Log.e("tag", "onReceiveLocation: " + startPoi.latitude + " 我的位置" + startPoi.longitude);
            startLocationOverlap();//开启我的位置图层

            LatLng nodeLocation = new LatLng(houseList.get(0).getLatitude(), houseList.get(0).getLongitude());
            mBaiduMap.setMapStatus(MapStatusUpdateFactory.newLatLng(nodeLocation));

        }

    }

    private void updateMapMaker(int selectPosition) {
        selectPosition = selectPosition%houseList.size();
        mBaiduMap.clear();
        for (int i = 0; i < houseList.size(); i++) {
            int color = Color.RED;
            if (i == selectPosition) {
                color = Color.parseColor("#436EEE");
            }
            ServicePointBean house = houseList.get(i);
            addMerchantMaker(house.getLatitude(), house.getLongitude(), color);
        }

        LatLng location = new LatLng(houseList.get(selectPosition).getLatitude(), houseList.get(selectPosition).getLongitude());
        MapStatusUpdate Status = MapStatusUpdateFactory.newLatLng(location);
        mBaiduMap.animateMapStatus(Status);
    }

    private void addMerchantMaker(double latitude, double longitude, int color) {
        //定义Maker坐标点
        LatLng point = new LatLng(latitude, longitude);

        BitmapDescriptor bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(
                BitmapUtil.BitmapChangeColor(BitmapFactory.decodeResource(getResources(), R.drawable.ic_map_marker_grey600_24dp), color));

        //构建MarkerOption，用于在地图上添加Marker
        OverlayOptions option = new MarkerOptions()
                .position(point)
                .icon(bitmapDescriptor);

        //在地图上添加Marker，并显示
        mBaiduMap.addOverlay(option);


        if (isSingleHouse) {
            endPoi = point;
            switch (WAY) {
                case WAY_BIKE:
                    mapUtils.PaintBikingRoute(MapActivity.this, mBaiduMap, startPoi, endPoi);
                    break;
                case WAY_CAR:
                    mapUtils.PaintDrivingRoute(MapActivity.this, mBaiduMap, startPoi, endPoi);
                    break;
                case WAY_WALK:
                    mapUtils.PaintWalkingRoute(MapActivity.this, mBaiduMap, startPoi, endPoi);
                    break;
                case WAY_BUS:
                    mapUtils.PaintTransitRoute(MapActivity.this, mBaiduMap, startPoi, endPoi);
                    break;
            }
            //设置导航按钮可见
            nextStep.setVisibility(View.VISIBLE);
            preStep.setVisibility(View.VISIBLE);
        }
    }

    public void addBottomFragments() {

        for (ServicePointBean house : houseList) {
            linearLayout = (LinearLayout) this.getLayoutInflater().inflate(R.layout.item_bottom_map,null);
            TextView titleBottom = (TextView) linearLayout.findViewById(R.id.title_item_map_bottom);
            TextView summaryBottom = (TextView) linearLayout.findViewById(R.id.summary_item_map_bottom);
            titleBottom.setText(house.getServicePointName());
            summaryBottom.setText(house.getIntroduce());
            bottomPager.add(linearLayout);
        }

        ItemMapBottomAdapter adapter = new ItemMapBottomAdapter(bottomPager);

        mViewPager.setAdapter(adapter);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;

            case R.id.menu_map_myLocation:
                mLocationClient.start();
                startLocationOverlap();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_map, menu);
        return true;
    }

    @Override
    protected void onDestroy() {
        //在activity执行onDestroy时执行mMapView.onDestroy()，实现地图生命周期管理
        mPoiSearch.destroy();
        mMapView.onDestroy();
        // 退出时销毁定位
        mLocationClient.stop();
        // 关闭定位图层
        mBaiduMap.setMyLocationEnabled(false);
        mMapView = null;
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        //在activity执行onResume时执行mMapView. onResume ()，实现地图生命周期管理
        mMapView.onResume();
        super.onResume();
    }

    @Override
    protected void onPause() {
        //在activity执行onPause时执行mMapView. onPause ()，实现地图生命周期管理
        mMapView.onPause();
        super.onPause();

    }


    @Override
    public void onBackPressed() {
        finish();
        overridePendingTransition(R.anim.activity_in_back, R.anim.activity_out_back);
        super.onBackPressed();
    }
}
