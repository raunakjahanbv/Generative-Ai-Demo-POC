package com.example.reviewtip;

import com.google.gson.JsonElement;
import org.json.simple.JSONArray;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.simple.JSONObject;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;


@Controller
@RequestMapping("/aimodal")
public class ReviewTipController {

    @ResponseBody
    @RequestMapping("/reviewtip")
    public JSONObject getReviewTips(@RequestParam("productname") String productName, @RequestParam("brandname") String brandName) throws IOException {

        JSONObject responseObject = new JSONObject();
        if (productName.isEmpty() ) {
            responseObject.put("status", 400);
            responseObject.put("error", "query params are missing");
            return responseObject;
        }

        String[] response = getReviewTipsBased(productName, brandName);
        if (response[1].equals("200")) {
            responseObject.put("status", 200);
            responseObject.put("data", response[0]);
        } else if (response[1].equals("500")) {
            responseObject.put("status", 500);
            responseObject.put("error", "Internal error");
            return responseObject;
        } else if (response[1].equals("401")) {
            responseObject.put("status", 401);
            responseObject.put("error", "Unauthorized");
            return responseObject;
        }
        return responseObject;
    }

    public static String[] getReviewTipsBased(String productName, String brandName) throws IOException {
        String[] res = new String[2];
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost("https://api.openai.com/v1/chat/completions");
        String apiKey = "Provide your api key";
        String inputText = "Act as a product reviews expert and influencer.Your instructions are to " +
                "give me tips and ideas to write a helpful and thorough product review. You can only give " +
                "me up to 5 simple two sentence tips in bullet format. You will use the tones - brief, crisp " +
                "and succinct. Make sure that the reviews are honest.Do not return any explanations or long paragraphs." +
                " Do not give tips on comparison with competitors or mention any retailers. Do not use any leading " +
                "language and make sure to keep the tips unbiased.The product name is " + productName + " The brand name is " + brandName + ".";
        System.out.println("Review: "+inputText.length());

        String model = "gpt-3.5-turbo";
        int maxTokens = 1000;
        httpPost.addHeader("Content-Type", "application/json");
        httpPost.addHeader("Authorization", "Bearer " + apiKey);
        JSONObject insideJsonObject =new JSONObject();
        String role="user";
        String content=inputText;
        insideJsonObject.put("role", role);
        insideJsonObject.put("content",content);
        JSONArray jsonArray=new JSONArray();
        jsonArray.add(insideJsonObject);
        JSONObject json = new JSONObject();
        json.put("model", model);
        json.put("max_tokens", maxTokens);
        json.put("messages", jsonArray);
        StringEntity entity = new StringEntity(json.toString());
        httpPost.setEntity(entity);
        CloseableHttpResponse httpResponse = httpClient.execute(httpPost);
        if (httpResponse.getStatusLine().getStatusCode() == 401) {
            res[1] = "401";
            return res;
        }

        try {
            HttpEntity responseEntity = httpResponse.getEntity();
            String responseBody = EntityUtils.toString(responseEntity);
            Gson gson = new Gson();
            JsonObject object = gson.fromJson(responseBody, JsonObject.class);
            JsonArray choicesArray = object.getAsJsonArray("choices");

            for (int i = 0; i < choicesArray.size(); i++) {
                JsonObject choiceObject = (JsonObject) choicesArray.get(i);
                JsonObject message= (JsonObject) choiceObject.get("message");
                JsonElement text= message.get("content");
                res[0] = text.getAsString();
                res[1] = httpResponse.getStatusLine().getStatusCode() + "";
            }
        }
        catch (Exception e)
        {
            res[0]="";
            res[1]="500";
        }
        finally {
            httpResponse.close();
        }
        return res;
    }


    @ResponseBody
    @RequestMapping("/questionanswer")
    public JSONObject questionAnswer(@RequestParam("productname") String productName, @RequestParam("brandname") String brandName,
                                     @RequestParam("productDescription") String productDescription, @RequestParam("questionText") String questionText, @RequestParam("passkey") String passkey, @RequestParam("filter") String productId) throws Exception {

            JSONObject responseObject = new JSONObject();
            JSONArray list = new JSONArray();
            if (productName.isEmpty()  || productDescription.isEmpty() || questionText.isEmpty()) {
                responseObject.put("status", 400);
                responseObject.put("error", "query params are missing");
                return responseObject;
            }
            if(!productId.isEmpty())
            {
                productId=productId.substring(6,productId.length());
            }
            String[] response = getAnswersBasedOnQuestionText(productName, brandName, productDescription, questionText, passkey, productId);
            String res = response[0];
            JSONObject insideResponseObject = new JSONObject();
            Map<String, String> map = new HashMap<>();
            try{
                   if(!res.isEmpty())
                    {
                        String res1=res.replace("\"{","");
                        String res2=res1.replace("\n","");
                        String res3=res2.replace("}\"","");
                        String res4=res3.replaceAll("\\\\","/");
                        String res5= res4.replace("/n","");
                        String res6= res5.replace("/","");
                        String[] arr = res6.split(",");
                        String[] str=new String[2];
                        for (String s : arr) {
                            if(s.contains(":"))
                            {
                                String[] kv = s.split(":");
                                String key = kv[0].replaceAll("[^a-zA-Z0-9]", "").trim();
                                String value = kv[1].replaceAll("[\\{\\}\\n\\\\]", "").trim();
                                str[0]=key;
                                str[1]=value;
                                map.put(key, value);
                            }
                            else {
                               map.put(str[0], str[1]+","+s);
                               str[1]=str[1]+","+s;
                            }

                        }
                        System.out.println(map);
                        insideResponseObject.put("content_contains_answer", map.get("contentcontainsanswer"));
                        insideResponseObject.put("justification", map.get("justification"));
                        insideResponseObject.put("answer", map.get("answer"));
                    } else if (res.equalsIgnoreCase("")) {
                        insideResponseObject.put("content_contains_answer", "false");
                        insideResponseObject.put("justification", "The content does not provide enough information to definitively answer the customer's question.");
                        insideResponseObject.put("answer", "");
                    }

            if (response[1].equals("200")) {
                if(!response[0].isEmpty())
                {
                    responseObject.put("status", 200);
                    responseObject.put("data", insideResponseObject);
                }
                else if(response[0].equalsIgnoreCase("")) {
                    insideResponseObject.put("content_contains_answer", "false");
                    insideResponseObject.put("justification", "The content does not provide enough information to definitively answer the customer's question.");
                    insideResponseObject.put("answer", "");
                    responseObject.put("data", insideResponseObject);

                }
            } else if (response[1].equals("500")) {
                responseObject.put("status", 500);
                responseObject.put("error", "Internal error");
                return responseObject;
            } else if (response[1].equals("401")) {
                responseObject.put("status", 401);
                responseObject.put("error", "Unauthorized");
                return responseObject;
            }
                return responseObject;
        }
            catch (Exception e)
        {
            responseObject.put("status", 500);
            responseObject.put("error", "Internal error");
            return responseObject;
        }

        }


    public static String[] getAnswersBasedOnQuestionText(String productName, String brandName, String productDescription, String questionText, String passkey, String productId) throws IOException {

        String[] res = new String[2];
        double threshold=Double.NEGATIVE_INFINITY;
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost("https://api.openai.com/v1/completions");
        String apiKey = "Provide your api key";
        StringBuilder newProductDescription=new StringBuilder(productDescription);
        String value=TechSpecsJson.getValueFromKey(productId);
        String finalnewProductDescription=newProductDescription.append(value).toString();
        finalnewProductDescription=finalnewProductDescription.replaceAll("[^\\x00-\\x7F]", "");
        String inputText = "Product:"+ productName+"\n" +
                "Brand Name:"+ brandName +"\n" +
                "Description:"+ finalnewProductDescription +
                "First, as a customer support representative, determine if you are able to definitively answer, without a doubt, the customer question about this product and brand given the content about the product.Answer in a professional, friendly and helpful tone.+" +
                "Do not answer questions about inventory, stock, price, shipping, safety, financial, legal, medical. Second, respond with a JSON in the format: {\"content_contains_answer\": boolean, // true or false. Whether the information in the content is sufficient to definitively answer the question without a doubt. \n" +
                "justification: string // Why you believe the answer you found is or is not sufficient to answer the question. \n" +
                "answer: string  // Your answer, or an empty string if you do not have a good response}\n" +
                "Question Text: "+ questionText+"\n" +
                "\n" +
                "###\n" +
                "\n" +
                "A: \n" +
                "\n";

        String model = "text-davinci-003";
        int maxTokens = 1000;
        int logprobs_value = 1;
        httpPost.addHeader("Content-Type", "application/json");
        httpPost.addHeader("Authorization", "Bearer " + apiKey);
        JSONObject json = new JSONObject();
        json.put("prompt", inputText);
        json.put("model", model);
        json.put("max_tokens", maxTokens);
        json.put("logprobs", logprobs_value);
        StringEntity entity = new StringEntity(json.toString());
        httpPost.setEntity(entity);
        CloseableHttpResponse httpResponse = httpClient.execute(httpPost);
        if (httpResponse.getStatusLine().getStatusCode() == 401) {
            res[1] = "401";
            return res;
        }
        try {
            HttpEntity responseEntity = httpResponse.getEntity();
            String responseBody = EntityUtils.toString(responseEntity);
            Gson gson = new Gson();
            JsonObject object = gson.fromJson(responseBody, JsonObject.class);
            JsonArray choicesArray = object.getAsJsonArray("choices");

            for (int i = 0; i < choicesArray.size(); i++) {
                JsonObject choiceObject = (JsonObject) choicesArray.get(i);
                String text = String.valueOf(choiceObject.get("text"));
                JsonObject logprobs = (JsonObject) choiceObject.get("logprobs");
                String tokens = String.valueOf(logprobs.get("tokens"));
                String[] strArray = tokens.split(",");
                String[] newStrArray = new String[strArray.length];

                int k = 0;
                for (String x : strArray) {
                    String result = x.replaceAll("^\"|\"$", "");
                    newStrArray[k] = result;
                    k++;
                }
                String tokenLogprobs = String.valueOf(logprobs.get("token_logprobs"));
                String[] strprobs = tokenLogprobs.split(",");
                double ans = getMeanTokenLogprob(newStrArray, strprobs);
                    if(ans>=threshold)
                    {
                        res[0]=text;
                        res[1]=httpResponse.getStatusLine().getStatusCode() + "";
                    }
                    else  {
                        res[0]="";
                        res[1]=httpResponse.getStatusLine().getStatusCode() + "";
                    }

            }
        }
        catch (Exception e)
        {
            res[0]="";
            res[1]="500";
        }finally {
            httpResponse.close();
        }
        return res;
    }

    public static double getMeanTokenLogprob(String[] tokens, String[] tokenLogprobs) {
        // first find where the answer begins
        int answerOccurences = 0;
        for (String token : tokens) {
            if (token.equals("answer")) {
                answerOccurences++;
            }
        }
        int answerOccurencesFound = 0;
        int tokenIdx = 0;
        for (String token : tokens) {
            if (token.equals("answer")) {
                answerOccurencesFound++;
            }
            if (answerOccurencesFound >= answerOccurences) {
                break;
            }
            tokenIdx++;
        }

        if(tokenLogprobs.length-1>tokenIdx+2)
        {
            String[] tlb = Arrays.copyOfRange(tokenLogprobs, tokenIdx + 2, tokenLogprobs.length-1);
            // convert token_logprobs from string to list of floats
            double[] tlbDouble = new double[tlb.length];
            for (int i = 0; i < tlb.length; i++) {
                tlbDouble[i] = Double.parseDouble(tlb[i]);
            }
            // we'll use mean. we could experiment on this more, though.
            if (tlbDouble.length > 1) {
                return Arrays.stream(tlbDouble).average().orElse(0);
            } else {
                return tlbDouble[0];
            }
        }
        else{
            return  0;
        }

    }
}

