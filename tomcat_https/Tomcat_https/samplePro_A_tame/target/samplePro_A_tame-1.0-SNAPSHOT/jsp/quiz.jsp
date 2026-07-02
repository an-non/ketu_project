<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<%@ page import="java.util.List"%>
<%@ taglib prefix="c" uri="jakarta.tags.core"%>
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<title>AI簡易クイズシステム</title>
<style>
body {
	font-family: 'Helvetica Neue', Arial, 'Hiragino Kaku Gothic ProN',
		Meiryo, sans-serif;
	background-color: #f4f6f9;
	color: #333;
	margin: 0;
	padding: 20px;
}

.container {
	max-width: 700px;
	margin: 0 auto;
	background: #ffffff;
	padding: 30px;
	border-radius: 8px;
	box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
}

h1, h2 {
	text-align: center;
	color: #2c3e50;
}

.form-group {
	margin-bottom: 20px;
}

label {
	display: block;
	font-weight: bold;
	margin-bottom: 8px;
}

input[type="text"] {
	width: 100%;
	padding: 12px;
	font-size: 16px;
	border: 1px solid #ccc;
	border-radius: 4px;
	box-sizing: border-box;
}

button, input[type="submit"] {
	background-color: #3498db;
	color: white;
	border: none;
	padding: 12px 20px;
	font-size: 16px;
	border-radius: 4px;
	cursor: pointer;
	width: 100%;
	font-weight: bold;
}

button:hover, input[type="submit"]:hover {
	background-color: #2980b9;
}

.question-card {
	background: #fdfdfd;
	border: 1px solid #eef2f5;
	padding: 20px;
	margin-bottom: 25px;
	border-radius: 6px;
}

.question-text {
	font-size: 17px;
	font-weight: bold;
	margin-bottom: 12px;
	color: #2c3e50;
}

.option-label {
	display: block;
	background: #f8fafc;
	border: 1px solid #e2e8f0;
	padding: 12px;
	margin-bottom: 8px;
	border-radius: 4px;
	cursor: pointer;
	transition: background 0.2s;
}

.option-label:hover {
	background: #edf2f7;
}

.option-label input {
	margin-right: 10px;
}

.result-card {
	border-left: 5px solid #bdc3c7;
	padding-left: 15px;
	margin-bottom: 20px;
}

.result-card.correct {
	border-left-color: #2ecc71;
	background-color: #ebfaf0;
	padding: 15px;
	border-radius: 0 4px 4px 0;
}

.result-card.incorrect {
	border-left-color: #e74c3c;
	background-color: #fdf2f2;
	padding: 15px;
	border-radius: 0 4px 4px 0;
}

.score-box {
	text-align: center;
	font-size: 24px;
	font-weight: bold;
	margin-bottom: 30px;
	padding: 15px;
	background: #e8f4fc;
	border-radius: 6px;
	color: #2980b9;
}

.explanation {
	font-size: 14px;
	color: #555;
	margin-top: 8px;
	font-style: italic;
}

.back-link {
	display: block;
	text-align: center;
	margin-top: 20px;
	color: #3498db;
	text-decoration: none;
	font-weight: bold;
}

.msgBox.error {
	background-color: #fdf2f2;
	color: #e74c3c;
	padding: 12px;
	border-radius: 4px;
	margin-bottom: 20px;
	border: 1px solid #f8d7da;
	text-align: center;
}
</style>
</head>
<body>

	<div class="container" id="quiz-container">

		<%-- エラーメッセージの表示 --%>
		<c:if test="${errorMsg != null}">
			<div class="msgBox error">
				<c:out value="${errorMsg}" />
			</div>
		</c:if>

		<%
        Object stateObj = request.getAttribute("state");
        String state = (stateObj != null) ? (String) stateObj : "input";
        Object quizObj = session.getAttribute("quiz");
        
        if ("input".equals(state)) {
    %>
		<h1>AIクイズジェネレーター</h1>
		<p style="text-align: center; color: #666; margin-bottom: 30px;">Gemini
			AIがあなたの指定したテーマで瞬時にオリジナルの4択クイズを作成します。</p>

		<form action="${pageContext.request.contextPath}/QuizServlet"
			method="POST">
			<input type="hidden" name="action" value="generate">
			<div class="form-group" id="input-group">
				<label for="topic">学習したいテーマ、またはお題を入力してください：</label> <input
					type="text" id="topic" name="topic"
					placeholder="例：Java基礎, 日本の戦国時代, 世界のご当地料理" required>
			</div>
			<button type="submit" id="btn-generate">クイズを生成する（API同期呼出）</button>
		</form>
		<%
        } else if ("play".equals(state) && quizObj != null) {
            String title = (String) request.getAttribute("quizTitle");
            String description = (String) request.getAttribute("quizDescription");
            List<java.util.Map<String, Object>> questions = (List<java.util.Map<String, Object>>) request.getAttribute("questionsList");
    %>
		<h2><%= title %></h2>
		<p style="color: #666; text-align: center; margin-bottom: 25px;"><%= description %></p>

		<form action="${pageContext.request.contextPath}/QuizServlet"
			method="POST">
			<input type="hidden" name="action" value="grade">

			<% 
                if (questions != null) {
                    for (int i = 0; i < questions.size(); i++) {
                        java.util.Map<String, Object> q = questions.get(i);
                        String qText = (String) q.get("questionText");
                        List<String> options = (List<String>) q.get("options");
            %>
			<div class="question-card" id="q-card-<%= i %>">
				<div class="question-text">
					問
					<%= (i + 1) %>.
					<%= qText %></div>
				<div class="options-container" id="options-<%= i %>">
					<% for (int j = 0; j < options.size(); j++) { %>
					<label class="option-label" id="label-<%= i %>-<%= j %>"> <input
						type="radio" name="ans_<%= i %>" value="<%= j %>" required>
						<%= options.get(j) %>
					</label>
					<% } %>
				</div>
			</div>
			<% 
                    }
                } 
            %>

			<button type="submit" id="btn-grade">回答を送信して採点する</button>
		</form>
		<%
        } else if ("result".equals(state)) {
            Integer score = (Integer) request.getAttribute("score");
            Integer total = (Integer) request.getAttribute("total");
            List<java.util.Map<String, Object>> resultsList = (List<java.util.Map<String, Object>>) request.getAttribute("resultsList");
    %>
		<h2>採点結果</h2>
		<div class="score-box" id="score-display">
			得点:
			<%= score %>
			/
			<%= total %>
			問
		</div>

		<% 
            if (resultsList != null) {
                for (int i = 0; i < resultsList.size(); i++) {
                    java.util.Map<String, Object> res = resultsList.get(i);
                    String qText = (String) res.get("questionText");
                    List<String> options = (List<String>) res.get("options");
                    int userAns = (Integer) res.get("userAns");
                    int correctAns = (Integer) res.get("correctAns");
                    String exp = (String) res.get("explanation");
                    boolean isCorrect = (userAns == correctAns);
        %>
		<div class="question-card <%= isCorrect ? "correct" : "incorrect" %>"
			id="res-card-<%= i %>">
			<div class="question-text" style="margin-bottom: 8px;">
				問
				<%= (i + 1) %>.
				<%= qText %>
				<span
					style="float: right; font-weight: bold; color: <%= isCorrect ? "#2ecc71" : "#e74c3c" %>;">
					<%= isCorrect ? "〇 正解" : "× 不正解" %>
				</span>
			</div>
			<div style="clear: both;"></div>

			<p style="margin: 5px 0;">
				あなたの回答: <strong><%= userAns >= 0 ? options.get(userAns) : "未回答" %></strong>
			</p>
			<p style="margin: 5px 0; color: #2ecc71;">
				正解: <strong><%= options.get(correctAns) %></strong>
			</p>

			<div class="explanation" id="exp-<%= i %>">
				<strong>解説:</strong>
				<%= exp %>
			</div>
		</div>
		<% 
                }
            } 
        %>

		<a href="${pageContext.request.contextPath}/QuizServlet?action=reset"
			class="back-link" id="link-restart">別のテーマでクイズを遊ぶ</a>
		<%
        }
    %>
	</div>

</body>
</html>