
import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.WebhookClientBuilder;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.*;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.jsoup.Connection.*;


public class FT_Monitor {

    private static final long webHookToken = 0L;
    private final static String[] DEFAULT_USER_AGENTS = {
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_13_2) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/65.0.3325.181 Safari/537.36",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Ubuntu Chromium/65.0.3325.181 Chrome/65.0.3325.181 Safari/537.36",
            "Mozilla/5.0 (Linux; Android 7.0; Moto G (5) Build/NPPS25.137-93-8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/64.0.3282.137 Mobile Safari/537.36",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 7_0_4 like Mac OS X) AppleWebKit/537.51.1 (KHTML, like Gecko) Version/7.0 Mobile/11B554a Safari/9537.53",
            "Mozilla/5.0 (Windows NT 6.1; Win64; x64; rv:60.0) Gecko/20100101 Firefox/60.0",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.13; rv:59.0) Gecko/20100101 Firefox/59.0",};

    private static final String calendarJSONAddress = "https://www.footlocker.es/INTERSHOP/static/WFS/Footlocker-Site/-/Footlocker/en_US/Release-Calendar/Launchcalendar/launchdata/launchcalendar_feed_all.json";
    private static final String VIEW_DOMAIN = "https://www.footlocker.es/INTERSHOP/web/WFS/Footlocker-Footlocker_ES-Site/es_ES/-/EUR/ViewProduct-ProductVariationSelect?BaseSKU="; 
    private static final String JSON_ID_STRING = "data-product-variation-info-json='";
    private static final String VIEW_ADDRESS = "https://www.footlocker.es/es/p/nikeee-air-force-1-low-hombre-zapatillas-46?v=";

    private static final String PRODUCTS_DIRECTORY = "products.txt";
    private static final String PRODUCT_DATA_DIRECTORY = "product_data.txt";

    private static String[] global_product_ids = readProductIDs();

    private static HashMap<String,JSONObject> productInformationMap = readProductData(); // Must be called after product ids are read!!
    private static HashMap<String,ProductData> currentStockData = new HashMap<>();

    private static final String manuString = "manufacturerSku";

    private static LFUList<Character> prevCharList = new LFUList<>(manuString.length());
    private static String botToken = "";

    private static String[] readProductIDs(){
        try{
            BufferedReader reader = new BufferedReader(new FileReader(PRODUCTS_DIRECTORY));
            ArrayList<String> tmp = new ArrayList<>();
            while(reader.ready()) tmp.add(reader.readLine());
            reader.close();
            return tmp.toArray(new String[tmp.size()]);
        }catch (IOException ex){
            System.out.println("Failed to read product data");
            System.out.println("Exception: " + ex.toString());
        }
        return null;
    }

    private static HashMap<String,JSONObject> readProductData(){
        HashMap<String,JSONObject> map = new HashMap<>();
        try{
            BufferedReader reader = new BufferedReader(new FileReader(PRODUCT_DATA_DIRECTORY));
            while(reader.ready()){
                String currentLine = reader.readLine();
                String[] splitLine = currentLine.split("@");
                if(splitLine.length>1) map.put(splitLine[0],new JSONObject(splitLine[1]));

            }
            reader.close();
        }catch (IOException ex){
            System.out.println("Failed to read product data");
            System.out.println("Exception: " + ex.toString());
        }
        return map;
    }

    private static void writeProductData(){
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(PRODUCT_DATA_DIRECTORY));
            productInformationMap.forEach((x,y)-> {
                try {
                    if(y!=null) writer.write(x + "@" + y.toString() + "/\n");
                } catch (IOException e) {
                    System.out.println("Failed to write Product Data : " + e.toString());
                }
            });
            writer.close();
        } catch (IOException e) {
            System.out.println("Failed to write Product Data : " + e.toString());
        }
    }


    private static Response getProductResponse(String address) {
        try{
            return Jsoup.connect(VIEW_ADDRESS + address)
                    .userAgent(DEFAULT_USER_AGENTS[(int)(Math.random()*DEFAULT_USER_AGENTS.length)])
                    .ignoreContentType(true)
                    .execute();
        }catch (Exception e){
            return null;
        }
    }

    private static String getCalendarJSONString() throws IOException {
               try{
                   return Jsoup.connect(calendarJSONAddress)
                           .userAgent(DEFAULT_USER_AGENTS[(int)(Math.random()*DEFAULT_USER_AGENTS.length)])
                           .ignoreContentType(true)
                           .execute()
                           .body();
               }catch (SocketTimeoutException e){
                    return getCalendarJSONString();
               }
    }


    public static JSONObject getJSONFromCalendar(String calendar_JSON, String product_id){
        prevCharList.clear();

        int id_index = calendar_JSON.indexOf(product_id);
        if(id_index==-1) return new JSONObject();

        int start_index = id_index;

        while(true){
            start_index--;
            String prevString = prevCharList.stream().map(String::valueOf).collect(Collectors.joining());
            char c = calendar_JSON.charAt(start_index);
            prevCharList.add(c);
            if(prevString.contains(":\"di\""))break;
        }

        int counter=0;
        prevCharList.clear();
        char currentChar = calendar_JSON.charAt(start_index+counter);

        while(true) {
            counter++;
            prevCharList.add(currentChar);
            String prevString = prevCharList.stream().map(String::valueOf).collect(Collectors.joining());
            if(prevString.equals(manuString)) break;
            currentChar = calendar_JSON.charAt(start_index+counter);
        }

        int end_index = start_index+counter;
        while(currentChar!='}'){
            currentChar = calendar_JSON.charAt(end_index);
            end_index++;
        }

        String product_json = calendar_JSON.substring(start_index,end_index);
        return new JSONObject(product_json);
    }

    private static final String NAME_CLASS = "fl-product-details--headline";
    private static final String PRICE_CLASS = "fl-product-details--price";

    public static void main(String[] args) throws IOException, InterruptedException {

        System.out.println("Reading product information"); // vL04ZaDJDN-58LXEuyACg8WZMQxWJNYK4ywzC2Fha81DtQZCVnu8FAgGgZPZUE7TjTJQ, 610940877220872193
        WebhookClient webhookClient = new WebhookClientBuilder(webHookToken,botToken).setWait(false).build();
        String calendarJSON = getCalendarJSONString();

        for (String global_product_id : global_product_ids) {

            JSONObject productCalendarJSON = getJSONFromCalendar(calendarJSON, global_product_id);
            JSONObject productJSON = productInformationMap.getOrDefault(global_product_id, new JSONObject());

            if (!productCalendarJSON.isEmpty() && productCalendarJSON.has("deepLinks")) {
                //JSONArray array = productCalendarJSON.getJSONArray("deepLinks");
                //JSONObject obj = array.getJSONObject(4);
                //es_product_ids[i] = obj.getString("link").split("=")[1];
                productJSON.put("image", productCalendarJSON.get("image").toString());
            }

            if (!productJSON.has("name") || !productJSON.has("price")) {
                Response productResponse = getProductResponse(global_product_id);
                Document d = Objects.requireNonNull(productResponse).parse();

                if (!productJSON.has("name")) {
                    String nameString = d.getElementsByClass(NAME_CLASS).text();
                    productJSON.put("name", nameString);
                }

                if (!productJSON.has("price")) {
                    Document pDoc = Jsoup.parse(d.getElementsByClass(PRICE_CLASS).text());
                    productJSON.put("price", pDoc.getElementsByTag("body").text().replace("IVA incluido", ""));
                }
            }

            productInformationMap.put(global_product_id, productJSON);
        }

        writeProductData();
        Thread.sleep(5000);

        while(true) {

            long startTime = System.currentTimeMillis();

            for (int i = 0; i < global_product_ids.length; i++) {

                if(productInformationMap.get(global_product_ids[i])==null) continue;
                String product_id1 = global_product_ids[i];

                ProductData lastProductData = null;
                if (currentStockData.containsKey(product_id1)) lastProductData = currentStockData.get(product_id1);

                String rawSiteString;
                Connection SITE;

                    try{
                        SITE = Jsoup.connect(VIEW_DOMAIN + product_id1)
                                .userAgent(DEFAULT_USER_AGENTS[(int)(Math.random()*DEFAULT_USER_AGENTS.length)])
                                .ignoreContentType(true);
                        rawSiteString = cleanSiteString(SITE.get().toString());
                    }catch (Exception e){
                        System.out.println("Error connecting : " + e.getMessage() + "\n" + Arrays.toString(e.getStackTrace()));
                        continue;
                    }

                if(!rawSiteString.contains(JSON_ID_STRING)){
                  if(!currentStockData.containsKey(product_id1)){
                      currentStockData.put(product_id1,new ProductData(rawSiteString.toLowerCase().contains("sold out")));
                      if(!rawSiteString.toLowerCase().contains("sold out")) System.out.println(rawSiteString);
                      System.out.println("Set sold out for " + product_id1);
                  }else{
                      ProductData lastData = currentStockData.get(product_id1);
                      if(!lastData.isSoldOut()){
                          lastData.toggleSoldout();
                          currentStockData.put(product_id1,lastData);
                          System.out.println("Set sold out for existing stock data! " + product_id1);
                      }
                  }
                  continue;
                }
                    String extractedJSON = extractJSONString(rawSiteString);
                    JSONObject productJSON = new JSONObject(extractedJSON);

                    boolean restocked = false;
                    ArrayList<String> restock = new ArrayList<>();

                    for (String key : productJSON.keySet()) { // For each different type ( each size has a different unique ID)
                        JSONObject uniqueProductNew = productJSON.getJSONObject(key);

                        if (lastProductData!=null) {
                            JSONObject uniqueProductOld = lastProductData.getData().getJSONObject(key);
                            if(!uniqueProductNew.has("quantityoptions") && !uniqueProductNew.has("quantityOptions")){
                                System.out.println("Skipping product, JSON : " + uniqueProductNew.toString());
                                continue;
                            }

                            JSONArray newQuantityArray = uniqueProductNew.getJSONArray(uniqueProductNew.has("quantityoptions")?"quantityoptions":"quantityOptions");

                            if(lastProductData.isSoldOut() && newQuantityArray.length()>0){
                                restock.add(key);
                                restocked = true;
                            }else{
                                JSONArray oldQuantityArray = uniqueProductOld.getJSONArray(uniqueProductOld.has("quantityoptions")?"quantityoptions":"quantityOptions");
                                if ((oldQuantityArray.length() == 0) && newQuantityArray.length() > 0) {
                                    System.out.println("RESTOCK FOR: " + product_id1 + "(SPAIN ID)");
                                    restocked = true;
                                    restock.add(key);
                                }
                            }
                        }
                    }

                    currentStockData.put(product_id1,new ProductData(productJSON));

                    if(restocked){
                        String out = getOutputString(global_product_ids[i],restock,productJSON);
                        System.out.println(out);
                        webhookClient.send(out);
                    }

            }

            System.out.println("Checked for re-stock, elapsed time: " + (System.currentTimeMillis()-startTime) + "ms");


            try {
                Thread.sleep(Math.max((long) (60000L * Math.random()),20000));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private static String getOutputString(String productID, ArrayList<String> restockedList, JSONObject productJSON){

        JSONObject productInformation = productInformationMap.get(productID);

        String all = "";
        if(productInformation!=null){
            all += "Shoe: " + productInformation.getString("name");
            all+= "\n"; // To do: SB
            all+= "Price: " + (productInformation.has("price")?productInformation.get("price"):"?");
            all+= "\n";
        }

        StringBuilder ava = new StringBuilder("Available Size(s): ");
        StringBuilder restocked = new StringBuilder("Restocked Size(s): ");

        int counter=0;
        int restocked_counter = 0;

        for(String key : productJSON.keySet()){
            counter++;
            JSONObject uniqueProductNew = productJSON.getJSONObject(key);
            String size = uniqueProductNew.getString(uniqueProductNew.has("sizevalue")?"sizevalue":"sizeValue");

            JSONArray q = uniqueProductNew.getJSONArray(uniqueProductNew.has("quantityoptions")?"quantityoptions":"quantityOptions");

            if(q.length()>0) {
                ava.append(size);
                if(counter<productJSON.length()) ava.append(" ,");
            }

            if(restockedList.contains(key)){
                restocked_counter++;
                restocked.append(size);
                if(restocked_counter<restocked.length()) ava.append(" ,");

            }

        }

        ava.append("\n");
        return all +  ava.toString() + restocked.toString() + "\n";
    }

    private static String extractJSONString(String rawSiteString){
        int jsonStartIndex = rawSiteString.indexOf("data-product-variation-info-json='");
        int nextIndex = jsonStartIndex+JSON_ID_STRING.length();

        int count = 0;
        char current;

        while(true){
            current = rawSiteString.charAt(count+nextIndex);
            if(current=='\'') break;
            count++;
        }
        return rawSiteString.substring(nextIndex,nextIndex+count);
    }

    private static String cleanSiteString(String rawSiteString){
        rawSiteString = rawSiteString.replace("&quot;", "\"");
        if(rawSiteString.contains("&#47")){

            int indexOf = rawSiteString.indexOf("&#47");
            rawSiteString = rawSiteString.replace("&#47;","/");
            char currentChar = rawSiteString.charAt(indexOf);

            boolean firstPassed = false;
            while(true){
                if(Character.isDigit(currentChar)){
                    if(firstPassed)break;
                    else firstPassed=true;
                }
                else if(currentChar =='\"'){
                    char[] siteStringChars = rawSiteString.toCharArray();
                    siteStringChars[indexOf] = ' ';
                    rawSiteString = new String(siteStringChars);
                    break;
                }
                indexOf--;
                currentChar = rawSiteString.charAt(indexOf);
            }
        }

        if(rawSiteString.contains("producto estar&aacute;")){ // Move to single string
            int start_index = rawSiteString.indexOf("producto estar&aacute;");
            char current = rawSiteString.charAt(start_index);
            int count = 0;

            while(current!='\"'){
                count++;
                current = rawSiteString.charAt(start_index+count);
            }
            count++;

            int end_index = start_index+count;
            rawSiteString = rawSiteString.replace(rawSiteString.substring(start_index,end_index),"");
        }

        return rawSiteString;
    }

    private static class ProductData{
        private boolean soldOut=false;
        private JSONObject data;

        ProductData(JSONObject data){
            this.data = data;
        }

        ProductData(boolean isSoldOut){
            soldOut = isSoldOut;
        }

        boolean isSoldOut(){return soldOut;}
        JSONObject getData() {return data; }
        void toggleSoldout(){soldOut=!soldOut;}

        public void setJSONData(JSONObject data) {this.data = data;}

    }

}
