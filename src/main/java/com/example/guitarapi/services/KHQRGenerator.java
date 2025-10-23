package com.example.guitarapi.services;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import kh.org.nbc.bakong_khqr.BakongKHQR;
import kh.org.nbc.bakong_khqr.model.KHQRResponse;
import kh.org.nbc.bakong_khqr.model.CRCValidation;
import kh.org.nbc.bakong_khqr.model.IndividualInfo;
import kh.org.nbc.bakong_khqr.model.MerchantInfo;
import kh.org.nbc.bakong_khqr.model.KHQRCurrency;
import kh.org.nbc.bakong_khqr.model.KHQRData;
import kh.org.nbc.bakong_khqr.model.KHQRDeepLinkData;
import kh.org.nbc.bakong_khqr.model.SourceInfo;

@Service
public class KHQRGenerator {
    private static final Logger logger = LoggerFactory.getLogger(KHQRGenerator.class);
    
    // Merchant Configuration
    private static final String BAKONG_ACCOUNT = "saroth_leng@aclb";
    private static final String MERCHANT_CITY = "Phnom Penh";
    private static final String MERCHANT_BANK = "ACLEDA";
    private static final String STORE_LABEL = "Main Store";
    private static final String TERMINAL_LABEL = "Main Counter";
    private static final String MOBILE_NUMBER = ""; // Optional: Set if you have a contact number
    private static final String MERCHANT_NAME = "Pick And Play";
    
    /**
     * Generates an individual KHQR code string for person-to-person transactions
     * 
     * @param amount The transaction amount
     * @param currency The currency code (USD or KHR)
     * @return The generated KHQR code string
     * @throws RuntimeException if the generation fails
     */
    public String generateIndividualKHQR(Double amount, String currency) {
        try {
            IndividualInfo individualInfo = new IndividualInfo();
            
            // Set merchant account information
            individualInfo.setBakongAccountId(BAKONG_ACCOUNT);
            individualInfo.setAcquiringBank(MERCHANT_BANK);
            individualInfo.setMerchantName(MERCHANT_NAME);
            individualInfo.setMerchantCity(MERCHANT_CITY);
            
            // Set transaction details
            individualInfo.setCurrency(KHQRCurrency.valueOf(currency));
            individualInfo.setAmount(amount);
            
            // Set additional information
            individualInfo.setStoreLabel(STORE_LABEL);
            individualInfo.setTerminalLabel(TERMINAL_LABEL);
            if (!MOBILE_NUMBER.isEmpty()) {
                individualInfo.setMobileNumber(MOBILE_NUMBER);
            }
            
            // Khmer language support might require different API calls in this version
            
            // Generate QR
            KHQRResponse<KHQRData> response = BakongKHQR.generateIndividual(individualInfo);
            
            if (response != null && response.getKHQRStatus().getCode() == 0) {
                String qrString = response.getData().getQr();
                logger.info("Generated Individual KHQR string: {}", qrString);
                logger.info("MD5: {}", response.getData().getMd5());
                return qrString;
            } else {
                String error = response != null ? response.getKHQRStatus().getMessage() : "Unknown error";
                logger.error("Failed to generate Individual KHQR: {}", error);
                throw new RuntimeException("Failed to generate Individual KHQR: " + error);
            }
        } catch (Exception e) {
            logger.error("Error generating Individual KHQR: {}", e.getMessage());
            throw new RuntimeException("Failed to generate Individual KHQR", e);
        }
    }
    
    /**
     * Generates a KHQR code string using merchant information (default method)
     * 
     * @param amount The transaction amount
     * @param currency The currency code (USD or KHR)
     * @return The generated KHQR code string
     */
    public String generateKHQRString(Double amount, String currency) {
        return generateMerchantKHQR(amount, currency);
    }

    public String generateMerchantKHQR(Double amount, String currency) {
        try {
            MerchantInfo merchantInfo = new MerchantInfo();
            
            // Set merchant account information
            merchantInfo.setBakongAccountId(BAKONG_ACCOUNT);
            merchantInfo.setMerchantId("PICKPLAY001");
            merchantInfo.setAcquiringBank(MERCHANT_BANK);
            merchantInfo.setMerchantName(MERCHANT_NAME);
            merchantInfo.setMerchantCity(MERCHANT_CITY);
            
            // Set transaction details
            merchantInfo.setCurrency(KHQRCurrency.valueOf(currency));
            merchantInfo.setAmount(amount);
            
            // Set additional information
            merchantInfo.setStoreLabel(STORE_LABEL);
            merchantInfo.setTerminalLabel(TERMINAL_LABEL);
            if (!MOBILE_NUMBER.isEmpty()) {
                merchantInfo.setMobileNumber(MOBILE_NUMBER);
            }
            
            // Khmer language support might require different API calls in this version
            
            // Generate QR
            KHQRResponse<KHQRData> response = BakongKHQR.generateMerchant(merchantInfo);
            
            if (response != null && response.getKHQRStatus().getCode() == 0) {
                String qrString = response.getData().getQr();
                logger.info("Generated Merchant KHQR string: {}", qrString);
                logger.info("MD5: {}", response.getData().getMd5());
                return qrString;
            } else {
                String error = response != null ? response.getKHQRStatus().getMessage() : "Unknown error";
                logger.error("Failed to generate Merchant KHQR: {}", error);
                throw new RuntimeException("Failed to generate Merchant KHQR: " + error);
            }
        } catch (Exception e) {
            logger.error("Error generating Merchant KHQR: {}", e.getMessage());
            throw new RuntimeException("Failed to generate Merchant KHQR", e);
        }
    }

    /**
     * Verifies the integrity and validity of a KHQR code string
     * 
     * @param qrCode The KHQR code string to verify
     * @return true if the code is valid, false otherwise
     * @throws RuntimeException if the verification process fails
     */
    public boolean verifyKHQR(String qrCode) {
        try {
            KHQRResponse<CRCValidation> response = BakongKHQR.verify(qrCode);
            boolean isValid = response != null && 
                            response.getKHQRStatus().getCode() == 0 && 
                            response.getData().isValid();
            
            logger.info("KHQR verification result: {}", isValid);
            return isValid;
        } catch (Exception e) {
            logger.error("Error verifying KHQR: {}", e.getMessage());
            throw new RuntimeException("Failed to verify KHQR", e);
        }
    }

    /**
     * Generates a deep link for the KHQR code that can be used to open the Bakong app
     * 
     * @param qrCode The KHQR code string to generate a deep link for
     * @return The generated deep link URL
     * @throws RuntimeException if deep link generation fails
     */
    @Value("${app.bakong.deeplink_callback:https://picknplay.app/payment/callback}")
    private String appDeepLinkCallback;

    public String generateDeepLink(String qrCode) {
        try {
            String url = "https://bakong.nbc.gov.kh/api/v1/generate_deeplink_by_qr";

            SourceInfo sourceInfo = new SourceInfo();
            sourceInfo.setAppName("Pick And Play");
            sourceInfo.setAppIconUrl("https://picknplay.app/icon.png");
            // Use configurable callback so developers can point to ngrok or production URLs
            sourceInfo.setAppDeepLinkCallback(this.appDeepLinkCallback);

            KHQRResponse<KHQRDeepLinkData> response = BakongKHQR.generateDeepLink(url, qrCode, sourceInfo);

            if (response != null && response.getKHQRStatus().getCode() == 0) {
                String deepLink = response.getData().getShortLink();
                logger.info("Generated deep link: {}", deepLink);
                return deepLink;
            } else {
                String error = response != null ? response.getKHQRStatus().getMessage() : "Unknown error";
                logger.error("Failed to generate deep link: {}", error);
                throw new RuntimeException("Failed to generate deep link: " + error);
            }
        } catch (Exception e) {
            logger.error("Error generating deep link: {}", e.getMessage());
            throw new RuntimeException("Failed to generate deep link", e);
        }
    }

    /**
     * Generate a deep link by first creating a Merchant KHQR and then requesting
     * the deep link for that merchant QR. This ensures the deep link corresponds
     * to the merchant generation flow.
     *
     * @param amount the transaction amount
     * @param currency the currency code (USD or KHR)
     * @return the generated deep link (short link) or null if generation fails
     */
    public String generateDeepLinkFromMerchant(Double amount, String currency) {
        try {
            // First generate a Merchant KHQR using the same data as generateMerchantKHQR
            MerchantInfo merchantInfo = new MerchantInfo();
            merchantInfo.setBakongAccountId(BAKONG_ACCOUNT);
            merchantInfo.setMerchantId("PICKPLAY001");
            merchantInfo.setAcquiringBank(MERCHANT_BANK);
            merchantInfo.setMerchantName(MERCHANT_NAME);
            merchantInfo.setMerchantCity(MERCHANT_CITY);
            merchantInfo.setCurrency(KHQRCurrency.valueOf(currency));
            merchantInfo.setAmount(amount);
            merchantInfo.setStoreLabel(STORE_LABEL);
            merchantInfo.setTerminalLabel(TERMINAL_LABEL);
            if (!MOBILE_NUMBER.isEmpty()) {
                merchantInfo.setMobileNumber(MOBILE_NUMBER);
            }

            KHQRResponse<KHQRData> merchantResp = BakongKHQR.generateMerchant(merchantInfo);
            if (merchantResp == null || merchantResp.getKHQRStatus().getCode() != 0) {
                String error = merchantResp != null ? merchantResp.getKHQRStatus().getMessage() : "Unknown error";
                logger.error("Failed to generate Merchant KHQR for deep link: {}", error);
                return null;
            }

            String merchantQr = merchantResp.getData().getQr();
            logger.info("Generated merchant QR for deep link: {}", merchantQr);

            // Now request the deep link for that merchant QR
            String url = "https://bakong.nbc.gov.kh/api/v1/generate_deeplink_by_qr";
            SourceInfo sourceInfo = new SourceInfo();
            sourceInfo.setAppName("Pick And Play");
            sourceInfo.setAppIconUrl("https://picknplay.app/icon.png");
            sourceInfo.setAppDeepLinkCallback(this.appDeepLinkCallback);

            KHQRResponse<KHQRDeepLinkData> dlResp = BakongKHQR.generateDeepLink(url, merchantQr, sourceInfo);
            if (dlResp != null && dlResp.getKHQRStatus().getCode() == 0) {
                String deepLink = dlResp.getData().getShortLink();
                logger.info("Generated deep link from merchant flow: {}", deepLink);
                return deepLink;
            } else {
                String error = dlResp != null ? dlResp.getKHQRStatus().getMessage() : "Unknown error";
                logger.warn("Failed to generate deep link from merchant flow: {}", error);
                return null;
            }
        } catch (Exception e) {
            logger.error("Error generating deep link from merchant flow: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Generates a QR code image from the given KHQR string
     * 
     * @param qrData The KHQR code string to convert to an image
     * @return byte array containing the PNG image data
     * @throws IOException if image generation fails
     */
    public byte[] generateQRImage(String qrData) throws IOException {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(qrData, BarcodeFormat.QR_CODE, 350, 350);
            
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(MatrixToImageWriter.toBufferedImage(bitMatrix), "PNG", outputStream);
            return outputStream.toByteArray();
        } catch (WriterException e) {
            logger.error("Error generating QR image: {}", e.getMessage());
            throw new IOException("Failed to generate QR image", e);
        }
    }
}