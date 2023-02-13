package com.example.serverless;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.spec.PutItemSpec;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClientBuilder;
import com.amazonaws.services.simpleemail.model.*;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;


public class EmailNotification implements RequestHandler<SNSEvent, Object> {

    private DynamoDB dynamoDB;
    private static String EMAIL_SUBJECT = "Verify Email for Account Creation";
    private static final String EMAIL_SENDER = "noreply@prod.avinashraikesh.me";

    //Calculation of Time To Live(TTL)
    long bufferPeriod = 60 * 2;
    long secondsSinceEpoch = Instant.now().getEpochSecond();
    long expirationTime = secondsSinceEpoch + bufferPeriod;

    public EmailNotification() {
        dynamoDB = new DynamoDB(AmazonDynamoDBClientBuilder
                .standard()
                .withRegion(Regions.US_EAST_1)
                .build());
    }

    @Override
    public Object handleRequest(SNSEvent request, Context context) {

        //Initializing Logger for Lambda
        LambdaLogger logger = context.getLogger();
        String username = "";
        String token = "";
        String emailMsg = "";

        //Check if SNS request is null
        if (request.getRecords() == null) {
            logger.log("No records found!");
            return null;
        }

        //logging the Events
        logger.log("domain: " + EMAIL_SENDER);
        logger.log("SNS Published Event: " + request);
        logger.log("TTL Expiration Time: " + expirationTime);

        String jsonString = request.getRecords().get(0).getSNS().getMessage();

        logger.log("jsonString: " + jsonString);

        try {
            JSONObject jsonObject = new JSONObject(jsonString);
            username = jsonObject.getString("username");
            token = jsonObject.getString("token_uuid");
            emailMsg = jsonObject.getString("email_body");
        } catch (JSONException e) {
            logger.log("Exception while parsing json");
            e.printStackTrace();
        }

        Item item = dynamoDB.getTable("myDynamoDBEmail").getItem("id", username);
        if (item == null)
        {
            this.dynamoDB.getTable("myDynamoDBEmail").putItem(new PutItemSpec()
                    .withItem(new Item().withString("id", username)));

            this.dynamoDB.getTable("myDynamoDB").putItem(new PutItemSpec()
                    .withItem(new Item().withString("id", token)
                            .withString("username", username)
                            .withLong("ttl", expirationTime)));

            logger.log("Record created in Dynamo DB for username: " + username);

            Content content = new Content().withData(emailMsg);
            Body emailBody = new Body().withText(content);
            AmazonSimpleEmailService emailService =
                    AmazonSimpleEmailServiceClientBuilder.defaultClient();
            SendEmailRequest emailRequest = new SendEmailRequest()
                    .withDestination(new Destination().withToAddresses(username))
                    .withMessage(new Message()
                            .withBody(emailBody)
                            .withSubject(new Content().withCharset("UTF-8").withData(EMAIL_SUBJECT)))
                    .withSource(EMAIL_SENDER);
            emailService.sendEmail(emailRequest);
            logger.log("Email Sent!");
        }
        else
        {
            logger.log("Verification link has already been sent once for user: " + username);
        }
        return null;
    }

}
