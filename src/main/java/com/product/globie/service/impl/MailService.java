package com.product.globie.service.impl;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class MailService {
    @Autowired
    private JavaMailSender mailSender;

    public void sendNewMail(String to, String subject, String body,String fullname) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(body.replace("{recipient_email}", fullname), true); // Replace {recipient_email} with actual recipient email

        mailSender.send(message);
    }

    public void sendOTPtoActiveAccount(String to, String otp,String fullname) throws MessagingException {
        String subject = "OTP to active account - Globie";
        String body = "<html>" +
                "<body>" +
                "<h2 style=\"color: #0D6EFD;\">OTP code</h2>" +
                "<p>Dear " +  fullname +",</p>" +
                "<p>We received a request to active your account for the Globie account associated with this email address. If you did not request this change, you can ignore this email.</p>" +
                "<p>To active your account, please use the following OTP code:</p>" +
                "<h3 style=\"color: #0D6EFD;\">" + otp + "</h3>" +
                "<p>This OTP code will expire in 15 minutes.</p>" +
                "<p>Thank you for using Globie!</p>" +
                "<p>Best regards,<br/>Globie</p>" +
                "</body>" +
                "</html>";
        sendNewMail(to, subject, body,fullname);
    }
}