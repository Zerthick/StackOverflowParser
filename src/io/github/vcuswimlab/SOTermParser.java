package io.github.vcuswimlab;

import com.sun.xml.internal.txw2.output.IndentingXMLStreamWriter;

import javax.xml.namespace.QName;
import javax.xml.stream.*;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class for parsing dump of StackOverflow usage: java -jar SOTermParser input.xml output.xml termcutoff
 */
public class SOTermParser {

    private final static String POST_TAG = "row";
    private static Map<String, Long> collectionTfMap;
    private static Map<String, Long> collectionDfMap;
    private static long docCount;

    private final static String ENTITY_SIZE_LIMIT_PROPERTY = "jdk.xml.totalEntitySizeLimit";

    public static void main(String[] args) {

        Long startTime = System.currentTimeMillis();

        System.setProperty(ENTITY_SIZE_LIMIT_PROPERTY, String.valueOf(Integer.MAX_VALUE));

        collectionTfMap = new HashMap<>();
        collectionDfMap = new HashMap<>();
        docCount = 0L;

        if(args.length == 3) {
            XMLInputFactory inputFactory = XMLInputFactory.newInstance();
            try {
                XMLEventReader eventReader = inputFactory.createXMLEventReader(new FileReader(args[0]));

                System.out.println("Indexing Terms...");
                while (eventReader.hasNext()) {
                    XMLEvent event = eventReader.nextEvent();

                    if(event.getEventType() == XMLStreamConstants.START_ELEMENT) {
                        StartElement startElement = event.asStartElement();
                        if(startElement.getName().getLocalPart().equals(POST_TAG)) {
                            processPost(startElement.getAttributeByName(QName.valueOf("Body")).getValue());
                            docCount++;
                        }
                    }
                }
            } catch (XMLStreamException | FileNotFoundException e) {
                e.printStackTrace();
            }

            XMLOutputFactory outputFactory = XMLOutputFactory.newInstance();
            try {
                XMLStreamWriter streamWriter = new IndentingXMLStreamWriter(outputFactory.createXMLStreamWriter(new FileWriter(args[1])));

                System.out.println("Writing Output Stats...");
                streamWriter.writeStartDocument();

                streamWriter.writeStartElement("Collection");
                streamWriter.writeAttribute("termCount", String.valueOf(collectionTfMap.size()));
                streamWriter.writeAttribute("docCount", String.valueOf(docCount));

                streamWriter.writeStartElement("Terms");
                collectionTfMap.entrySet().stream()
                        .filter(e -> e.getValue() > Long.valueOf(args[2])).forEach(e -> {
                            double idf = Math.log10( (double) docCount / collectionDfMap.get(e.getKey()));
                            double ictf = Math.log10( (double) collectionTfMap.size() / e.getValue());
                    try {
                        streamWriter.writeStartElement("Term");
                        streamWriter.writeAttribute("name", e.getKey());
                        streamWriter.writeAttribute("ctf", String.valueOf(e.getValue()));
                        streamWriter.writeAttribute("df", String.valueOf(collectionDfMap.get(e.getKey())));
                        streamWriter.writeAttribute("idf", String.valueOf(idf));
                        streamWriter.writeAttribute("ictf", String.valueOf(ictf));
                        streamWriter.writeEndElement();
                    } catch (XMLStreamException e1) {
                        e1.printStackTrace();
                    }
                });

                streamWriter.writeEndElement();
                streamWriter.writeEndElement();
                streamWriter.writeEndDocument();
                streamWriter.close();

                System.out.println("Done!");
            } catch (XMLStreamException | IOException e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("Incorrect number of arguments! \n" +
                    "Correct Syntax: SOTermParser <input_file.xml> <output_file.xml> <cutoff>");
        }

        System.clearProperty(ENTITY_SIZE_LIMIT_PROPERTY);

        long endTime = System.currentTimeMillis();

        System.out.println("Total Runtime: " + (endTime - startTime));
    }


    private static Pattern pattern = Pattern.compile("\\b[a-z]+.*?\\b");
    private static void processPost(String post) {

        post = post.toLowerCase();
        post = post.replaceAll("\\s+", " ");
        post = post.replaceAll("(<.*?>|['\"])", "");

        Map<String,Long> tfMap = new HashMap<>();

        Matcher matcher = pattern.matcher(post);
        while (matcher.find()) {


            String term = matcher.group();

            if(term.length() > 2) {
                tfMap.put(term, 1L + tfMap.getOrDefault(term, 0L));
            }
        }

        tfMap.entrySet().forEach(stringLongEntry -> {

            String key = stringLongEntry.getKey();
            long value = stringLongEntry.getValue();

            collectionTfMap.put(key, collectionTfMap.getOrDefault(key, 0L) + value);
            collectionDfMap.put(key, collectionDfMap.getOrDefault(key, 0L) + 1);
        });
    }
}
