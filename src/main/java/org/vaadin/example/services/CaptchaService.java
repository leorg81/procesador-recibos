package org.vaadin.example.services;

import org.springframework.stereotype.Service;

import java.util.Random;

@Service
public class CaptchaService {
    private final Random random = new Random();

    public CaptchaChallenge generateMathChallenge() {
        int num1 = random.nextInt(10) + 1; // Números del 1 al 10
        int num2 = random.nextInt(10) + 1;
        int operator = random.nextInt(3); // 0: +, 1: -, 2: *

        String question;
        int answer;

        switch(operator) {
            case 0:
                question = num1 + " + " + num2 + " = ?";
                answer = num1 + num2;
                break;
            case 1:
                question = num1 + " - " + num2 + " = ?";
                answer = num1 - num2;
                break;
            case 2:
                question = num1 + " × " + num2 + " = ?";
                answer = num1 * num2;
                break;
            default:
                question = num1 + " + " + num2 + " = ?";
                answer = num1 + num2;
        }

        return new CaptchaChallenge(question, String.valueOf(answer));
    }

    public boolean validateChallenge(String userAnswer, String correctAnswer) {
        return userAnswer != null && userAnswer.trim().equals(correctAnswer);
    }

    public record CaptchaChallenge(String question, String answer) {}
}