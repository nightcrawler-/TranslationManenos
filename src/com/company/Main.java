package com.company;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.*;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by frederick on 7/5/16.
 */
public class Main {

    private static final String FILE = "/home/frederick/Desktop/data.xlsx";
    private static final String FILE_OUT = "/home/frederick/Desktop/data2.xlsx";
    private static final String MODDED_FILE = "/home/frederick/Desktop/data2.xlsx";

    private static final String OUT_DIR = "/home/frederick/Desktop/";


    public static final String API_KEY = "AIzaSyBKmBj3xh0_y1EwN8JUjb8eeVbZiUaeVTw";
    public static final String BASE_URL = "https://www.googleapis.com/language/translate/v2?key=" + API_KEY + "&source=en";
    public static final String[] LOCALES = {"es", "de", "sw"};

    public static final String[] SAMPLE = {"how are you", "big foot", "i used to hate you", "what is the time in your zone", "that's about enough, aye"};


    public static final int TITLES = 1;
    public static final int ICUCODES_ROW = 2;
    public static final int DEF_TRANSLATIONS_ROW = 4;
    public static final int WORDS_EN_COLUMN = 1;

    private static final int OFFSET = 2;

    /**
     * Key - locale, value - list of translated words
     */
    public static HashMap<String, List<String>> translations = new HashMap<>();


    public static void main(String... args) throws IOException, InvalidFormatException, NoSuchAlgorithmException {
        translate();

        createLocalFilesFromModdedTranslations();
    }

    private static void translate() throws IOException, InvalidFormatException, NoSuchAlgorithmException {
        Workbook wb = WorkbookFactory.create(new File(FILE));

        Holder dataPair = getDataPairs(wb);

        for (String locale : dataPair.locales) {
            System.out.println("translating to: " + locale);

            List<String> translated = new ArrayList<>();
            JsonReader reader = Json.createReader(new StringReader(connect(buildUrl(dataPair.words, locale))));
            JsonObject result = reader.readObject();
            reader.close();

            JsonObject data = result.getJsonObject("data");
            JsonArray translationsRaw = data.getJsonArray("translations");

            for (int i = 0; i < translationsRaw.size(); i++) {
                System.out.println("tra: " + translationsRaw.getJsonObject(i).getString("translatedText"));
                translated.add(translationsRaw.getJsonObject(i).getString("translatedText"));
            }

            translations.put(locale, translated);
        }

        insertData(wb, translations, dataPair.locales, dataPair.words);
        FileOutputStream fileOut = new FileOutputStream(FILE_OUT);
        wb.write(fileOut);
        fileOut.close();

        //createLocaleFiles(dataPair.locales, dataPair.words, translations);
        //createEnLocaleFile(dataPair.words);
    }

    private static void createLocalFilesFromModdedTranslations() throws IOException, InvalidFormatException, NoSuchAlgorithmException {
        Workbook wb = WorkbookFactory.create(new File(MODDED_FILE));
        TranslationsHolder dataPairs = getDataPairsTranslated(wb);
        createLocaleFiles(dataPairs.locales, dataPairs.translations.get("en"), dataPairs.translations);
    }

    /**
     * Separate for English as it is the default lang
     *
     * @param english
     * @throws IOException
     */
    private static void createEnLocaleFile(List<String> english) throws IOException, NoSuchAlgorithmException {
        FileOutputStream ous = new FileOutputStream(OUT_DIR + "en.yml");
        OutputStreamWriter writer = new OutputStreamWriter(ous);
        writer.append("en:\n");

        for (String translation : english) {
            writer.append(" " + createMD5Tag(translation) + ": '" + translation + "'\n");
        }

        writer.flush();
        writer.close();
        ous.flush();
        ous.close();
    }

    private static void createLocaleFiles(List<String> locales, List<String> english, HashMap<String, List<String>> translations) throws IOException, NoSuchAlgorithmException {

        for (String locale : locales) {
            FileOutputStream ous = new FileOutputStream(OUT_DIR + locale + ".yml");
            OutputStreamWriter writer = new OutputStreamWriter(ous);
            writer.append(locale + ":\n");

            int i = 0;
            for (String translation : translations.get(locale)) {
                writer.append(" " + createMD5Tag(english.get(i)) + ": '" + translation + "'\n");
                i++;
            }

            writer.flush();
            writer.close();
            ous.flush();
            ous.close();
        }

    }

    private static void insertData(Workbook wb, HashMap<String, List<String>> translations, List<String> locales, List<String> english) throws NoSuchAlgorithmException {

        for (Sheet sheet : wb) {
            int i = 0;
            for (Row row : sheet) {
                if (row.getRowNum() > DEF_TRANSLATIONS_ROW) {
                    if (i == translations.get("fr").size()) break;
                    for (String locale : locales) {
                        //get each locale, fetch translation for locale and add to appropriate column
                        createCell(row, locales.indexOf(locale) + OFFSET, translations.get(locale).get(i));
                        createCell(row, 0, createMD5Tag(english.get(i)));

                    }
                    i++;
                }

            }
        }
    }

    /**
     * prefer to use create md5 tag to avoid conflicts for large dictonaries
     *
     * @param title
     * @return
     */
    @Deprecated
    private static String createTag(String title) {
        if (title.length() > 10) {
            title = title.substring(0, 10);
        }
        return title.toLowerCase().replace(" ", "_");
    }

    private static String createMD5Tag(String title) throws NoSuchAlgorithmException {
        return toHexString(MessageDigest.getInstance("MD5").digest(title.toLowerCase().getBytes()));
    }

    public static String toHexString(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            String hex = Integer.toHexString(0xFF & bytes[i]);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private static void createCell(Row row, int column, String value) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
    }

    private static TranslationsHolder getDataPairsTranslated(Workbook wb) throws IOException, InvalidFormatException {

        HashMap<String, List<String>> translations = new HashMap<>();
        List<String> locales = new ArrayList<>();

        for (Sheet sheet : wb) {
            for (Row row : sheet) {
                for (Cell cell : row) {
                    cell.getColumnIndex();

                    if (cell.getColumnIndex() >= WORDS_EN_COLUMN && row.getRowNum() > DEF_TRANSLATIONS_ROW) {
                        translations.get(locales.get(cell.getColumnIndex() - 1)).add(cell.getStringCellValue());
                    }

                    //this will go first as cells are iterated left ro right
                    if (row.getRowNum() == ICUCODES_ROW && cell.getColumnIndex() >= WORDS_EN_COLUMN) {
                        locales.add(cell.getStringCellValue());
                        translations.put(cell.getStringCellValue(), new ArrayList<>());
                    }
                }
            }
        }

        return new TranslationsHolder(translations, locales);
    }

    private static Holder getDataPairs(Workbook wb) throws IOException, InvalidFormatException {

        List<String> words = new ArrayList<>();
        List<String> locales = new ArrayList<>();

        for (Sheet sheet : wb) {
            for (Row row : sheet) {
                for (Cell cell : row) {
                    cell.getColumnIndex();
                    // System.out.print(row.getRowNum() + ": ");
                    //System.out.println(cell.getStringCellValue() + ": " + cell.getColumnIndex());


                    //get list of english words
                    if (cell.getColumnIndex() == WORDS_EN_COLUMN && row.getRowNum() > DEF_TRANSLATIONS_ROW) {
                        words.add(cell.getStringCellValue());
                        //System.out.println("added: " + cell.getStringCellValue());
                    }
                    //translate to each locale
                    //append to locale cell
                    if (row.getRowNum() == ICUCODES_ROW && cell.getColumnIndex() > WORDS_EN_COLUMN) {
                        //System.out.println("icu code: " + cell.getStringCellValue());
                        locales.add(cell.getStringCellValue());
                    }
                }
            }
        }

        return new Holder(words, locales);
    }

    private static String buildUrl(List<String> data, String locale) throws UnsupportedEncodingException {
        String result = "&target=" + locale;

        int i = 0;
        List<String> older = data.subList(60, data.size());
        for (String s : data) {
            if (i == 67)
                break;
            //i++;
            result += "&q=" + s;
        }
        System.out.println(result);
        return BASE_URL + result.replace(" ", "%20");


    }

    private static String connect(String url) throws IOException {
        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();

        // optional default is GET
        con.setRequestMethod("GET");
        //add request header
        con.setRequestProperty("User-Agent", "JAVA");

        int responseCode = con.getResponseCode();
        System.out.println("\nSending 'GET' request to URL : " + url);
        System.out.println("Response Code : " + responseCode);

        BufferedReader in = new BufferedReader(
                new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuffer response = new StringBuffer();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        //System.out.println("Response : " + response.toString());


        return response.toString();
    }

    private static class Holder {
        List<String> words;
        List<String> locales;

        public Holder(List<String> words, List<String> locales) {
            this.words = words;
            this.locales = locales;
        }


    }

    private static class TranslationsHolder {
        HashMap<String, List<String>> translations;
        List<String> locales;

        public TranslationsHolder(HashMap<String, List<String>> translations, List<String> locales) {
            this.translations = translations;
            this.locales = locales;
        }
    }
}
