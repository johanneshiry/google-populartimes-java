/*
 * BSD 3-Clause License
 *
 * Copyright (c) 2019., Johannes Hiry
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.github.johanneshiry.populartimes;

import com.github.johanneshiry.populartimes.utils.GooglePlace;
import com.github.johanneshiry.populartimes.utils.LatLong;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.*;


public class PopularTimesCrawler {

    private int     radius;         // search distance in meters
    private boolean allPlaces;      // include/exclude places without popular times
    private String  apiKey;
    private String  type;           // type of the place that is used as filter
    private String  keyword;
    private boolean postFilter;
    //if true and keyword is provided, the results will be filtered to ensure that name contains keyword

    private ArrayList<LatLong> qRadar;  //list containing lat/long values to search for objects

    private ArrayList<GooglePlace> placesList; //list containing found googlePlaces objects

    //google stuff
    String radarUrl          =
                    "https://maps.googleapis.com/maps/api/place/nearbysearch/json?location=%s,%s&radius=%s&type=%s&keyword=%s&key=%s";
    String radarUrlNoKeyword =
                    "https://maps.googleapis.com/maps/api/place/nearbysearch/json?location=%s,%s&radius=%s&type=%s&key=%s";
    String detailUrl         = "https://maps.googleapis.com/maps/api/place/details/json?placeid=%s&key=%s";

    String userAgent =
                    "Mozilla/5.0 (iPhone; CPU iPhone OS 8_1_1 like Mac OS X) AppleWebKit/600.1.4 (KHTML, like Gecko) Mobile/12B435 mobile/iPhone OS/iPhone/iPhone6,1/8.1.1/KBS kong/1.0.8";

    public PopularTimesCrawler(String apiKey, String type) {

        this.allPlaces = false;
        this.postFilter = false;

        this.qRadar = new ArrayList<>();
        this.placesList = new ArrayList<>();

        this.apiKey = apiKey;
        this.type = type;

    }

    /**
     * get place information and popular times for a provided frame of min/max Coordinates via radius search
     *
     * @return
     */
    public ArrayList<GooglePlace> getDataOfFrame(LatLong minCoord, LatLong maxCoord, int radius) {

        ArrayList<GooglePlace> result;
        this.radius = radius;

        System.out.println("Starting radial search...");
        qRadar = getCircleCenters(minCoord, maxCoord, radius);
        ArrayList<String> ids = getIDs(qRadar);

        Set<String> uniqeIDs = new HashSet<String>(ids); //ensure unique values
        ids.clear();
        ids.addAll(uniqeIDs);

        System.out.println(ids.size() + " places to process...");

        result = getDetail(ids);

        return result;
    }

    /**
     * get place information and popular times by uniqe google id
     *
     * @param id
     * @return
     */
    public GooglePlace getDataFromID(String id) {

        ArrayList<String> ids = new ArrayList<>();
        ids.add(id);
        GooglePlace result = getDetail(ids).get(0);

        return result;
    }

    /**
     * get place information and popular times by locationName
     *
     * @param locName: location name
     * @param radius:  search radius
     * @return
     */
    public ArrayList<GooglePlace> getDataFromLocName(String locName, int radius) {
        ArrayList<GooglePlace> result = new ArrayList<>();

        this.radius = radius;

        //search for lat/long via google search
        LatLong point = getGeoLocByName(locName);
        ArrayList<LatLong> locList = new ArrayList<>();
        locList.add(point);

        //get the ids from api
        ArrayList<String> ids = getIDs(locList);

        //get the details
        result = getDetail(ids);

        return result;
    }

    /**
     * uses google/search to determine the lat/long values for api search
     * Example: https://www.google.de/search?tbm=map&hl=de&tch=1&q=Wuppertal-Elberfeld
     *
     * @param locName
     * @return
     */
    private LatLong getGeoLocByName(String locName) {

        JSONArray data = getSearchData(locName, "");

        JSONArray info = (JSONArray) ((JSONArray) data.get(1)).get(0);

        LatLong res = new LatLong((double) info.get(2), (double) info.get(1));

        return res;

    }

    /**
     * cover the search area with circles for radar search
     * http://stackoverflow.com/questions/7477003/calculating-new-longtitude-latitude-from-old-n-meters
     * radius parameter is taken from constructor
     *
     * @return ArrayList<String> with all ids of the discovered places
     */
    private ArrayList<LatLong> getCircleCenters(LatLong minCoord, LatLong maxCoord, int radius) {
        ArrayList<LatLong> res = new ArrayList<>();

        int r = 6378; //earth radius in km

        double minLat = minCoord.getLat();
        double minLong = minCoord.getLong();
        double maxLat = maxCoord.getLat();
        double maxLong = maxCoord.getLong();

        while(minLong < maxLong) {

            double tmp = minLat;

            while(tmp < maxLat) {
                res.add(new LatLong(tmp, minLong));
                tmp += (0.25 / r) * (radius / Math.PI);
            }

            minLong += (0.25 / r) * (radius / Math.PI) / Math.cos(minLat * Math.PI / radius);
        }
        return res;
    }

    /**
     * get list with unique google ids from places
     * query is executed via google API
     * places - radar search - https://developers.google.com/places/web-service/search?hl=de#RadarSearchRequests
     *
     * @param latLong
     * @return
     */

    private ArrayList<String> getIDs(ArrayList<LatLong> latLong) {
        ArrayList<String> ids = new ArrayList<>();

        for(int i = 0; i < latLong.size(); i++) {
            LatLong tmp = latLong.get(i);

            try {
                String radarString;
                if(keyword != null) {
                    radarString = String.format(radarUrl, URLEncoder.encode("" + tmp.getLat(), "UTF-8"),
                                    URLEncoder.encode("" + tmp.getLong(), "UTF-8"),
                                    URLEncoder.encode("" + radius, "UTF-8"), URLEncoder.encode(type, "UTF-8"),
                                    URLEncoder.encode("" + keyword, "UTF-8"), URLEncoder.encode("" + apiKey, "UTF-8"));
                } else {
                    radarString = String.format(radarUrlNoKeyword, URLEncoder.encode("" + tmp.getLat(), "UTF-8"),
                                    URLEncoder.encode("" + tmp.getLong(), "UTF-8"),
                                    URLEncoder.encode("" + radius, "UTF-8"), URLEncoder.encode(type, "UTF-8"),
                                    URLEncoder.encode("" + apiKey, "UTF-8"));
                }

                //                System.out.println(radarString);
                //do the json stuff
                InputStream inputStream = null;
                String json = "";

                HttpClient client = new DefaultHttpClient();
                HttpPost post = new HttpPost(radarString);
                HttpResponse response = client.execute(post);
                HttpEntity entity = response.getEntity();
                inputStream = entity.getContent();

                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "utf-8"), 8);
                StringBuilder sbuild = new StringBuilder();
                String line = null;
                while((line = reader.readLine()) != null) {
                    sbuild.append(line);
                }
                inputStream.close();
                json = sbuild.toString();

                //now parse
                JSONParser parser = new JSONParser();
                Object obj = parser.parse(json);
                JSONObject jb = (JSONObject) obj;

                //now read
                JSONArray jsonObject1 = (JSONArray) jb.get("results");

                //                System.out.println(jsonObject1.size());

                for(int j = 0; j < jsonObject1.size(); j++) {
                    JSONObject o = (JSONObject) jsonObject1.get(j);
                    ids.add((String) o.get("place_id"));
                }

            } catch(Exception e) {
                e.printStackTrace();
            }

        }

        return ids;
    }

    /**
     * requests detailed information from the google API based on the provided ID
     * places api - detail search - https://developers.google.com/places/web-service/details?hl=de
     *
     * @param ids: ArrayList<String> with all places ids that have to be searched for details
     * @return: ArrayList<GooglePlace> with detailed information about popular times and more
     */
    private ArrayList<GooglePlace> getDetail(ArrayList<String> ids) {

        for(int i = 0; i < ids.size(); i++) {

            String id = ids.get(i);

            try {
                String detailString = String.format(detailUrl, URLEncoder.encode("" + id, "UTF-8"),
                                URLEncoder.encode("" + apiKey, "UTF-8"));

                //                System.out.println(detailString);
                InputStream inputStream = null;
                String json = "";
                double rating = -1.0;
                int reviews = -1;

                HttpClient client = new DefaultHttpClient();
                HttpPost post = new HttpPost(detailString);
                HttpResponse response = client.execute(post);
                HttpEntity entity = response.getEntity();
                inputStream = entity.getContent();

                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "utf-8"), 8);
                StringBuilder sbuild = new StringBuilder();
                String line = null;
                while((line = reader.readLine()) != null) {
                    sbuild.append(line);
                }
                inputStream.close();
                json = sbuild.toString();

                //now parse
                JSONParser parser = new JSONParser();
                Object obj = parser.parse(json);
                JSONObject jb = (JSONObject) obj;

                //now read
                JSONObject jsonObject1 = (JSONObject) jb.get("result");

                String name = (String) jsonObject1.get("name"); //location name

                if(postFilter &&
                   keyword != null) //if we have post filter enabled and keyword != null, filter the results
                    if(!(name.toLowerCase().contains(keyword.toLowerCase()))) {
                        System.out.println("Skipped " + name + " due to filter settings.");
                        continue;
                    }

                String formattedAddress = (String) jsonObject1.get("formatted_address"); //full location address
                //get the types and convert them
                JSONArray jtypes = (JSONArray) jsonObject1.get("types");
                String[] types = new String[jtypes.size()];
                for(int k = 0; k < jtypes.size(); k++) {
                    types[k] = (String) jtypes.get(k);
                }
                LatLong latLong = new LatLong(((double) ((JSONObject) ((JSONObject) (jsonObject1.get("geometry")))
                                .get("location")).get("lat")),
                                ((double) ((JSONObject) ((JSONObject) (jsonObject1.get("geometry"))).get("location"))
                                                .get("lng")));

                JSONArray data = getSearchData((String) jsonObject1.get("name"),
                                (String) jsonObject1.get("formatted_address")); //get data from search request

                //size == 11 means no interesting information available
                //has to be corrected if google changes something
                if(((JSONArray) ((JSONArray) ((JSONArray) data.get(0)).get(1)).get(0)).size() > 11 &&
                   (JSONArray) ((JSONArray) ((JSONArray) ((JSONArray) data.get(0)).get(1)).get(0)) != null) {
                    //get information array
                    JSONArray info = (JSONArray) ((JSONArray) ((JSONArray) ((JSONArray) data.get(0)).get(1)).get(0))
                                    .get(14);
                    if(info.get(4) != null) {
                        if(((JSONArray) info.get(4)).get(7) != null) {
                            rating = (double) ((JSONArray) info.get(4)).get(7);
                        }

                        if(((JSONArray) info.get(4)).get(8) != null) {
                            reviews = (int) ((long) ((JSONArray) info.get(4)).get(8));
                        }
                    }

                    System.out.println(name);
                    System.out.println(formattedAddress);
                    if(info.get(84) == null) {
                        System.out.println("No information on popular times available!");
                    } else {
                        JSONArray jpopularTimes = (JSONArray) ((JSONArray) info.get(84)).get(0); //get popular times

                        //map popularTimes
                        Map<Integer, Map<Long, Double>> popularTimes = mapPopularTimes(jpopularTimes);

                        //create new googlePlaces object and fill it with data
                        GooglePlace place = new GooglePlace(name, formattedAddress, id, popularTimes, rating, latLong,
                                        types, reviews);

                        //add it to ArrayList<GooglePlace>
                        placesList.add(place);

                        //print one element
                        //                System.out.println(placesList.get(0).getName());
                        //                Map<Integer, Map<Long, Double>> pop = placesList.get(0).getPopularTimes();
                        //                for(int z = 0; z < pop.size(); z++) {
                        //                    Map<Long, Double> usages = pop.get(z);
                        //                    System.out.println("Day " + z);
                        //                    for(long u = 0; u < usages.size(); u++) {
                        //                        double usage = usages.get(u);
                        //                        for(int o= 0; o < (int)(usage/2); o++){
                        //                            System.out.print("=");
                        //                        }
                        //                        System.out.print(" " + usage);
                        //                        System.out.println();
                        //                    }
                        //                    System.out.println();
                        //                }
                    }
                }

            } catch(Exception e) {
                e.printStackTrace();
            }

        }
        return placesList;
    }

    /**
     * Converts the extracted popularTimes json data to a corresponding hashMap starting with a 0 as key for sunday and
     * 24 hours of usages
     *
     * @param jpopularTimes
     * @return
     */
    private Map<Integer, Map<Long, Double>> mapPopularTimes(JSONArray jpopularTimes) {

        Map<Integer, Map<Long, Double>> map = new HashMap<>();

        for(int i = 0; i < jpopularTimes.size(); i++) {
            Map<Long, Double> usages = new HashMap<>();

            //preinitialize usage map with zero values
            for(int k = 0; k < 24; k++) {
                usages.put((long) k, 0.0);
            }

            JSONArray day = (JSONArray) jpopularTimes.get(i);
            JSONArray hours = (JSONArray) day.get(1);
            if(hours == null) {
                System.out.println("Day " + i + " is closed or not enough data available.");
                map.put(i, usages);
            } else {
                for(int j = 0; j < hours.size(); j++) {

                    JSONArray hour = (JSONArray) hours.get(j);
                    usages.put((Long) hour.get(0), Double.valueOf((Long) hour.get(1)));
                }

                map.put(i, usages);
            }
        }

        return map;
    }

    /**
     * Sends a request to google/search and parses json response to get data
     *
     * @param name:             string with place name
     * @param formattedAddress: string with place address
     */
    private JSONArray getSearchData(String name, String formattedAddress) {

        JSONArray data = new JSONArray();
        try {
            String tbm = "map";
            String hl = "de";
            String tch = "1";
            String q = name + " " + formattedAddress;

            String appender = "tbm=" + tbm + "&hl=" + hl + "&tch=" + tch + "&q=" + URLEncoder.encode(q, "UTF-8");
            String searchUrl = "https://www.google.de/search?" + appender;

            //            System.out.println(searchUrl);

            URL url1 = new URL(searchUrl);
            URLConnection connection = url1.openConnection();
            connection.setRequestProperty("User-Agent", userAgent);
            connection.connect();

            InputStream response = connection.getInputStream();

            String json = "";
            BufferedReader reader = new BufferedReader(new InputStreamReader(response, "utf-8"), 8);
            StringBuilder sbuild = new StringBuilder();
            String line = null;
            while((line = reader.readLine()) != null) {
                sbuild.append(line);
            }
            response.close();
            json = sbuild.toString();

            int jEnd = json.lastIndexOf("}");
            if(jEnd >= 0)
                json = json.substring(0, jEnd + 1);

            //now parse
            JSONParser parser = new JSONParser();
            Object obj = parser.parse(json);
            JSONObject jb = (JSONObject) obj;

            //now read
            String jdata = (String) jb.get("d"); //read the data String
            jdata = jdata.substring(4, jdata.length()); //cut it to get the JSONArray again

            //reparse
            Object ob = parser.parse(jdata);
            data = (JSONArray) ob;

        } catch(Exception e) {
            e.printStackTrace();
        }

        return data;

    }

    /**
     * Set optional keyword parameter
     *
     * @param keyword
     */
    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    /**
     * save data in .xls file
     *
     * @param placesList
     */
    public void saveAsXls(ArrayList<GooglePlace> placesList) {

        try {
            Workbook wb = new HSSFWorkbook();
            //Workbook wb = new XSSFWorkbook();
            CreationHelper createHelper = wb.getCreationHelper();
            Sheet sheet = wb.createSheet("places");

            // Create a row and put some cells in it. Rows are 0 based.
            Row row = sheet.createRow((short) 0);

            //create headline cells
            row.createCell(0).setCellValue("name");
            row.createCell(1).setCellValue("address");
            row.createCell(2).setCellValue("placeID");
            row.createCell(3).setCellValue("rating");
            row.createCell(4).setCellValue("reviews");
            row.createCell(5).setCellValue("lat");
            row.createCell(6).setCellValue("long");
            row.createCell(7).setCellValue("types");

            //create first sheet
            Iterator<GooglePlace> it = placesList.iterator();
            for(int k = 0; k < placesList.size(); k++) {
                GooglePlace place = placesList.get(k);

                //create row
                row = sheet.createRow((short) (k + 1));

                //create data cells
                row.createCell(0).setCellValue(place.getName());
                row.createCell(1).setCellValue(place.getFormattedAddress());
                row.createCell(2).setCellValue(place.getPlaceId());
                row.createCell(3).setCellValue(place.getRating());
                row.createCell(4).setCellValue(place.getRating());
                row.createCell(5).setCellValue(place.getGeoCoord().getLat());
                row.createCell(6).setCellValue(place.getGeoCoord().getLong());

                for(int i = 0; i < place.getTypes().length; i++) {
                    row.createCell(i + 7).setCellValue(place.getTypes()[i]);
                }
            }

            //create second sheet
            Sheet sheet1 = wb.createSheet("details");

            // Create a row and put some cells in it. Rows are 0 based.
            row = sheet1.createRow((short) 0);

            //create headline cells
            row.createCell(0).setCellValue("name");
            row.createCell(1).setCellValue("address");
            row.createCell(2).setCellValue("sunday");
            row.createCell(3).setCellValue("monday");
            row.createCell(4).setCellValue("tuesday");
            row.createCell(5).setCellValue("wednesday");
            row.createCell(6).setCellValue("thursday");
            row.createCell(7).setCellValue("friday");
            row.createCell(8).setCellValue("saturday");

            //create data

            for(int j = 0; j < placesList.size(); j++) {
                GooglePlace place = placesList.get(j);

                //get populartimes
                Map<Integer, Map<Long, Double>> popularTimes = place.getPopularTimes();

                for(int q = 0; q < 24; q++) {

                    if(j == 0)
                        row = sheet1.createRow((short) q + 1);
                    if(j != 0)
                        row = sheet1.createRow((short) q + j * 24);

                    row.createCell(0).setCellValue(place.getName());
                    row.createCell(1).setCellValue(place.getFormattedAddress());

                    for(int o = 0; o < popularTimes.size(); o++) {

                        Map<Long, Double> times = popularTimes.get(o);

                        row.createCell(o + 2).setCellValue(times.get((long) q));

                    }

                }

            }

            // write the stuff
            FileOutputStream fileOut = new FileOutputStream("googlePlaces.xls");
            wb.write(fileOut);
            fileOut.close();

        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * if true and keyword is provided, the results will be filtered to ensure that name contains keyword
     *
     * @param postFilter
     */
    public void setPostFilter(boolean postFilter) {
        this.postFilter = postFilter;
    }
}

