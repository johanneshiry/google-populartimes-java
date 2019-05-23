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

package com.github.johanneshiry.populartimes.utils;

import com.google.gson.Gson;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;


public class GooglePlace {

    private final String                          name;
    private final String                          formattedAddress;
    private final String                          placeId;
    private final Map<Integer, Map<Long, Double>> popularTimes;
    private final double                          rating;
    private final LatLong                         geoCoord;
    private final String[]                        types;
    private final int                             review;

    public GooglePlace(String name,
                       String formattedAddress,
                       String placeId,
                       Map<Integer, Map<Long, Double>> popularTimes,
                       double rating,
                       LatLong geoCoord,
                       String[] types,
                       int review) {
        this.name = name;
        this.formattedAddress = formattedAddress;
        this.placeId = placeId;
        this.popularTimes = popularTimes;
        this.rating = rating;
        this.geoCoord = geoCoord;
        this.types = types;
        this.review = review;
    }

    public String getPlaceId() {
        return placeId;
    }

    public LatLong getGeoCoord() {
        return geoCoord;
    }

    public String getName() {
        return name;
    }

    public String getFormattedAddress() {
        return formattedAddress;
    }

    public String[] getTypes() {
        return types;
    }

    public Map<Integer, Map<Long, Double>> getPopularTimes() {
        return popularTimes;
    }

    public String toJSONString() {
        Gson gson = new Gson();

        return gson.toJson(this).toString();

    }

    public int getReview() {
        return review;
    }

    public double getRating() {
        return rating;
    }

    @Override
    public String toString() {
        return "GooglePlace{" + "name='" + name + '\'' + ", formattedAddress='" + formattedAddress + '\'' +
               ", placeId='" + placeId + '\'' + ", popularTimes=" + popularTimes + ", rating=" + rating +
               ", geoCoord=" + geoCoord + ", types=" + Arrays.toString(types) + ", review=" + review + '}';
    }

    @Override
    public boolean equals(Object o) {
        if(this == o)
            return true;
        if(o == null || getClass() != o.getClass())
            return false;
        GooglePlace that = (GooglePlace) o;
        return Double.compare(that.rating, rating) == 0 && review == that.review && Objects.equals(name, that.name) &&
               Objects.equals(formattedAddress, that.formattedAddress) && Objects.equals(placeId, that.placeId) &&
               Objects.equals(popularTimes, that.popularTimes) && Objects.equals(geoCoord, that.geoCoord) &&
               Arrays.equals(types, that.types);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(name, formattedAddress, placeId, popularTimes, rating, geoCoord, review);
        result = 31 * result + Arrays.hashCode(types);
        return result;
    }
}


