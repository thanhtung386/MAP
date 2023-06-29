/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.reader.osm.conditional;

import com.graphhopper.reader.ReaderWay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Inspects the conditional tags of an OSMWay according to the given conditional tags.
 * <p>
 *
 * @author Robin Boldt
 */
public class ConditionalOSMTagInspector implements ConditionalTagInspector {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final List<String> tagsToCheck;
    private final ConditionalParser permitParser, restrictiveParser;
    // enabling by default makes noise but could improve OSM data
    private boolean enabledLogs;

    public ConditionalOSMTagInspector(Calendar value, List<String> tagsToCheck,
                                      Set<String> restrictiveValues, Set<String> permittedValues) {
        this(Arrays.asList(new DateRangeParser(value)), tagsToCheck, restrictiveValues, permittedValues, false);
    }

    public ConditionalOSMTagInspector(List<? extends ConditionalValueParser> valueParsers, List<String> tagsToCheck,
                                      Set<String> restrictiveValues, Set<String> permittedValues, boolean enabledLogs) {
        this.tagsToCheck = new ArrayList<>(tagsToCheck.size());
        for (String tagToCheck : tagsToCheck) {
            this.tagsToCheck.add(tagToCheck + ":conditional");
        }

        this.enabledLogs = enabledLogs;

        // enable for debugging purposes only as this is too much
        boolean logUnsupportedFeatures = false;
        this.permitParser = new ConditionalParser(permittedValues, logUnsupportedFeatures);
        this.restrictiveParser = new ConditionalParser(restrictiveValues, logUnsupportedFeatures);
        for (ConditionalValueParser cvp : valueParsers) {
            permitParser.addConditionalValueParser(cvp);
            restrictiveParser.addConditionalValueParser(cvp);
        }
    }

    @Override
    public boolean isRestrictedWayConditionallyPermitted(ReaderWay way) {
        return applies(way, true);
    }

    @Override
    public boolean isPermittedWayConditionallyRestricted(ReaderWay way) {
        return applies(way, false);
    }

    protected boolean applies(ReaderWay way, boolean checkPermissiveValues) {
        if (way.getTags().size() < 2) return false; // minor speed up when only highway tag
        for (int index = 0; index < tagsToCheck.size(); index++) {
            String tagToCheck = tagsToCheck.get(index);
            String val = way.getTag(tagToCheck);
            if (val == null || val.isEmpty())
                continue;

            try {
                if (checkPermissiveValues) {
                    if (permitParser.checkCondition(val))
                        return true;
                } else {
                    if (restrictiveParser.checkCondition(val))
                        return true;
                }

            } catch (Exception e) {
                if (enabledLogs) {
                    // log only if no date ala 21:00 as currently date and numbers do not support time precise restrictions
                    if (!val.contains(":"))
                        logger.warn("for way " + way.getId() + " could not parse the conditional value '" + val + "' of tag '" + tagToCheck + "'. Exception:" + e.getMessage());
                }
            }
        }
        return false;
    }
}
