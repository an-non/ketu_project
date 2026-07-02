package com.quiz.servlet;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

@WebServlet("/QuizServlet")
public class QuizServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    // 環境変数または保護された秘密ファイルから API キーを取得する
    private static final String GEMINI_API_KEY = resolveGeminiApiKey();

    private static String resolveGeminiApiKey() {
        String fromEnv = System.getenv("GEMINI_API_KEY");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }

        String[] candidateFiles = {
                "/etc/ketu/secrets/gemini.env",
                "/opt/ketu/secrets/gemini.env"
        };

        for (String candidateFile : candidateFiles) {
            try {
                Path path = Paths.get(candidateFile);
                if (Files.exists(path)) {
                    for (String line : Files.readAllLines(path)) {
                        String trimmed = line.trim();
                        if (trimmed.startsWith("GEMINI_API_KEY=")) {
                            String value = trimmed.substring("GEMINI_API_KEY=".length()).trim();
                            if (!value.isEmpty()) {
                                return value;
                            }
                        }
                    }
                }
            } catch (IOException ignored) {
                // 署名付き秘密ファイルが無い場合は続行する
            }
        }

        return "";
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        String action = request.getParameter("action");
        HttpSession session = request.getSession();

        if ("reset".equals(action)) {
            session.removeAttribute("quiz");
            response.sendRedirect(request.getContextPath() + "/jsp/quiz.jsp");
            return;
        }

        request.setAttribute("state", "input");
        request.getRequestDispatcher("/jsp/quiz.jsp").forward(request, response);
    }
   

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        String action = request.getParameter("action");
        HttpSession session = request.getSession();

        if ("generate".equals(action)) {
            String topic = request.getParameter("topic");
            if (topic == null || topic.trim().isEmpty()) {
                topic = "一般的な一般常識";
            }

            try {
                // 最適化されたGsonメソッドを呼び出し
                Map<String, Object> quizMap = callGeminiAPIWithGson(topic);

                if (quizMap != null && quizMap.get("questions") != null && !((List<?>)quizMap.get("questions")).isEmpty()) {
                    session.setAttribute("quiz", quizMap);

                    request.setAttribute("state", "play");
                    request.setAttribute("quizTitle", quizMap.get("title"));
                    request.setAttribute("quizDescription", quizMap.get("description"));
                    request.setAttribute("questionsList", quizMap.get("questions"));
                } else {
                    request.setAttribute("errorMsg", "クイズデータの解析に失敗しました。お題を変えてお試しください。");
                    request.setAttribute("state", "input");
                }
            } catch (Exception e) {
                e.printStackTrace();
                request.setAttribute("errorMsg", "エラーが発生しました: " + e.getMessage());
                request.setAttribute("state", "input");
            }

            request.getRequestDispatcher("/jsp/quiz.jsp").forward(request, response);

        } else if ("grade".equals(action)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> quizMap = (Map<String, Object>) session.getAttribute("quiz");
            if (quizMap == null) {
                response.sendRedirect(request.getContextPath() + "/jsp/quiz.jsp");
                return;
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> questions = (List<Map<String, Object>>) quizMap.get("questions");
            int score = 0;
            int total = questions.size();
            List<Map<String, Object>> resultsList = new ArrayList<>();

            for (int i = 0; i < total; i++) {
                Map<String, Object> q = questions.get(i);
                String userAnsStr = request.getParameter("ans_" + i);
                int userAns = (userAnsStr != null) ? Integer.parseInt(userAnsStr) : -1;

                int correctAns = (Integer) q.get("correctAns");
                if (userAns == correctAns) {
                    score++;
                }

                Map<String, Object> result = new HashMap<>();
                result.put("questionText", q.get("questionText"));
                result.put("options", q.get("options"));
                result.put("userAns", userAns);
                result.put("correctAns", correctAns);
                result.put("explanation", q.get("explanation"));
                resultsList.add(result);
            }

            request.setAttribute("state", "result");
            request.setAttribute("score", score);
            request.setAttribute("total", total);
            request.setAttribute("resultsList", resultsList);

            request.getRequestDispatcher("/jsp/quiz.jsp").forward(request, response);
        }
    }

    /**
     * Gsonを利用してGemini APIからクイズデータを取得・パースするメソッド
     */
    private Map<String, Object> callGeminiAPIWithGson(String topic) throws Exception {
        if (GEMINI_API_KEY == null || GEMINI_API_KEY.isBlank()) {
            throw new IllegalStateException("GEMINI_API_KEY is not configured");
        }

        // 【重要】2026年現在の安定エンドポイントと正規モデル「gemini-2.5-flash」を指定
        String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + GEMINI_API_KEY;
        
        String prompt = "お題:「" + topic + "」について、4択クイズを5問作成してください。"
                + "以下のJSONフォーマットのみで出力してください。Markdown装飾は一切不要です。"
                + "{\"title\":\"...\", \"description\":\"...\", \"questions\":[{\"questionText\":\"...\", \"options\":[\"A\",\"B\",\"C\",\"D\"], \"correctAnswerIndex\":0, \"explanation\":\"...\"}]}";

        // GsonによるクリーンなリクエストJSONペイロードの構築
        JsonObject root = new JsonObject();
        JsonArray contents = new JsonArray();
        JsonObject part = new JsonObject();
        part.addProperty("text", prompt);
        JsonArray parts = new JsonArray();
        parts.add(part);
        JsonObject content = new JsonObject();
        content.add("parts", parts);
        contents.add(content);
        root.add("contents", contents);

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(root.toString()))
                .timeout(Duration.ofSeconds(30))
                .build();
        
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // HTTPステータスが200以外の場合は詳細をスロー
        if (response.statusCode() != 200) {
            throw new RuntimeException("API Error: " + response.statusCode() + " - " + response.body());
        }

        // GsonでAPIレスポンスの階層構造を安全にデコード
        JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();
        String rawText = jsonResponse.getAsJsonArray("candidates").get(0).getAsJsonObject()
                         .getAsJsonObject("content").getAsJsonArray("parts").get(0).getAsJsonObject()
                         .get("text").getAsString();

        // AIがマークダウンで囲ってきた場合の保険用除去処理
        if (rawText.contains("```")) {
            rawText = rawText.replace("```json", "").replace("```", "").trim();
        }

        // テキストをパースしてクイズ構造オブジェクトに変換
        JsonObject quizJson = JsonParser.parseString(rawText).getAsJsonObject();
        
        Map<String, Object> quizMap = new HashMap<>();
        quizMap.put("title", quizJson.get("title").getAsString());
        quizMap.put("description", quizJson.get("description").getAsString());

        List<Map<String, Object>> questionsList = new ArrayList<>();
        JsonArray qArray = quizJson.getAsJsonArray("questions");
        
        for (int i = 0; i < qArray.size(); i++) {
            JsonObject qObj = qArray.get(i).getAsJsonObject();
            Map<String, Object> qMap = new HashMap<>();
            
            qMap.put("questionText", qObj.get("questionText").getAsString());
            qMap.put("explanation", qObj.get("explanation").getAsString());
            
            // JSP側（Grade処理）で期待されているキー名「correctAns」に統一
            qMap.put("correctAns", qObj.get("correctAnswerIndex").getAsInt());
            
            List<String> options = new ArrayList<>();
            for (var opt : qObj.getAsJsonArray("options")) {
                options.add(opt.getAsString());
            }
            qMap.put("options", options);
            questionsList.add(qMap);
        }
        
        quizMap.put("questions", questionsList);
        return quizMap;
    }
}