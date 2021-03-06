package org.wso2.siddhi.extension.rssreader;

/*
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.wso2.siddhi.core.config.ExecutionPlanContext;
import org.wso2.siddhi.core.event.ComplexEventChunk;
import org.wso2.siddhi.core.event.stream.StreamEvent;
import org.wso2.siddhi.core.event.stream.StreamEventCloner;
import org.wso2.siddhi.core.event.stream.populater.ComplexEventPopulater;
import org.wso2.siddhi.core.exception.ExecutionPlanRuntimeException;
import org.wso2.siddhi.core.executor.ConstantExpressionExecutor;
import org.wso2.siddhi.core.executor.ExpressionExecutor;
import org.wso2.siddhi.core.query.processor.Processor;
import org.wso2.siddhi.core.query.processor.stream.StreamProcessor;
import org.wso2.siddhi.query.api.definition.AbstractDefinition;
import org.wso2.siddhi.query.api.definition.Attribute;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;


public class RSSFeedTitles extends StreamProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(RSSFeedTitles.class);
    private String keyword;
    private String urlString;
    private int passToOut;

    @Override
    public void start() {
        // Nothing to do here
    }

    @Override
    public void stop() {
        // Nothing to do here
    }

    @Override
    public Object[] currentState() {
        return null;
    }

    @Override
    public void restoreState(Object[] state) {
        // No need to maintain a state.
    }

    @Override
    protected void process(ComplexEventChunk<StreamEvent> streamEventChunk, Processor nextProcessor,
            StreamEventCloner streamEventCloner, ComplexEventPopulater complexEventPopulater) {
        ComplexEventChunk<StreamEvent> returnEventChunk = new ComplexEventChunk<StreamEvent>();
        StreamEvent streamEvent = streamEventChunk.getFirst();
        URL rssURL;
        if (urlString != null) {
            if (urlString.startsWith("https:")) {
                try {
                    rssURL = new URL(urlString);
                    TextArticles articles = readFeed(rssURL);
                    String[] titles = articles.getTitles();
                    String[] publishedDates = articles.getPublishedDate();
                    String[] descriptions = articles.getDescription();
                    String[] links = articles.getLinks();
                    int min = (passToOut > titles.length) ? titles.length : passToOut;
                    for (int i = 0; i < min; i++) {
                        StreamEvent clonedEvent = streamEventCloner.copyStreamEvent(streamEvent);
                        complexEventPopulater.populateComplexEvent(clonedEvent, new Object[] { titles[i],
                                descriptions[i], publishedDates[i], links[i], i + 1 });
                        returnEventChunk.add(clonedEvent);

                    }
                } catch (Exception e) {
                    LOGGER.error("error read RSS feeds in RSSReedTitles class " + e);
                }
            } else {
                throw new ExecutionPlanRuntimeException("Input to the RSS:Reader() function should contain URL");
            }
        } else {
            throw new ExecutionPlanRuntimeException("Input to the RSS:Reader() function cannot be null");
        }
        if (returnEventChunk.hasNext()) {
            nextProcessor.process(returnEventChunk);
        }

    }

    @Override
    protected List<Attribute> init(AbstractDefinition inputDefinition,
            ExpressionExecutor[] attributeExpressionExecutors, ExecutionPlanContext executionPlanContext) {
        if (attributeExpressionExecutors.length != 2) {
            throw new IllegalArgumentException("Invalid no of arguments passed to TwitterAnalytic:readRSSTitlesStream() function, "
                    + "required 1, but found " + attributeExpressionExecutors.length);
        }

        if (attributeExpressionExecutors[0] instanceof ConstantExpressionExecutor) {
            keyword = ((String) attributeExpressionExecutors[0].execute(null));
            try {
                urlString = "https://news.google.com/news?cf=all&hl=en&pz=1&ned=us&q="+ URLEncoder.encode(keyword, "UTF-8")+"&output=rss";
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }

        } else {
            throw new IllegalArgumentException("The first parameter should be an String");
        }
        if (attributeExpressionExecutors[1] instanceof ConstantExpressionExecutor) {
            passToOut = (Integer) (attributeExpressionExecutors[1].execute(null));
        } else {
            throw new IllegalArgumentException("The 2 parameter should be an integer");
        }

        List<Attribute> attributeList = new ArrayList<Attribute>();
        attributeList.add(new Attribute("Title", Attribute.Type.STRING));
        attributeList.add(new Attribute("Dis", Attribute.Type.STRING));
        attributeList.add(new Attribute("Pub", Attribute.Type.STRING));
        attributeList.add(new Attribute("Link", Attribute.Type.STRING));
        attributeList.add(new Attribute("Count", Attribute.Type.INT));
        return attributeList;
    }

    public TextArticles readFeed(URL rssURL) {
        String[] titles = null;
        String[] publishedDates = null;
        String[] descriptions = null;
        String[] links = null;
        try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = builder.parse(rssURL.openStream());
            NodeList items = doc.getElementsByTagName("item");
            titles = new String[items.getLength()];
            publishedDates = new String[items.getLength()];
            descriptions = new String[items.getLength()];
            links = new String[items.getLength()];
            for (int i = 0; i < items.getLength(); i++) {
                Element item = (Element) items.item(i);
                titles[i] = (String) getValue(item, "title");
                publishedDates[i] = (String) getValue(item, "pubDate");
                descriptions[i] = (String) getValue(item, "description");
                links[i] = (String) getValue(item, "link");
            }

        } catch (Exception e) {
            LOGGER.error("error read RSS feeds in readFeed function in RSSReedTitles class " + e);
        }
        return new TextArticles(links, titles, publishedDates, descriptions);
    }

    public String getValue(Element parent, String nodeName) {
        return parent.getElementsByTagName(nodeName).item(0).getFirstChild().getNodeValue();
    }

    private class TextArticles {
        private String[] titles;
        private String[] publishedDates;
        private String[] descriptions;
        private String[] links;

        public TextArticles(String[] links, String[] titles, String[] publishedDates, String[] descriptions) {
            this.links = links;
            this.titles = titles;
            this.publishedDates = publishedDates;
            this.descriptions = descriptions;
        }

        public String[] getLinks() {
            return this.links;

        }

        public String[] getTitles() {
            return this.titles;

        }

        public String[] getDescription() {
            return this.descriptions;

        }

        public String[] getPublishedDate() {
            return this.publishedDates;

        }
    }
}
