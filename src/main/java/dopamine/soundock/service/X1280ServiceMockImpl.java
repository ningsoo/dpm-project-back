package dopamine.soundock.service;

import dopamine.soundock.dto.response.PWLTokenResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "x1280.use-mock", havingValue = "true")
public class X1280ServiceMockImpl implements X1280Service {

    @Override
    public String isAp(String userId) {
        return "{\n" +
                "    \"result\": true,\n" +
                "    \"msg\": \"OK.\",\n" +
                "    \"code\": \"000.0\",\n" +
                "    \"data\": {\n" +
                "        \"exist\": false\n" +
                "    }\n" +
                "}";
    }

    @Override
    public String joinAp(String userId) {
        return "{\n" +
                "    \"result\": true,\n" +
                "    \"msg\": \"OK.\",\n" +
                "    \"code\": \"000.0\",\n" +
                "    \"data\": {\n" +
                "        \"qr\": \"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAASwAAAEsAQAAAABRBrPYAAAC8ElEQVR42u2aPa6kQAyEjSbokCNwE+ZiSIzExWZu0kcgJEB4q8popeG9YLNtSxCghvkIrLbLPz3m/3KtdmM3dmM3dmP/B9sM19atj2pPX2p5G1YdVnj9SII93at/fB/KYdMAVr/NFfclCTbCNGHW4cfywW3jarM+FcYtKkfvFYa/YC72Lh82DQVveMHf+C4RFv5GAiETjwgZ/90t28QU9WO/1MvtN3FoE/Mwd8Vmwekc0WJWPmGk58Aku37QtLF/mRHzw34IV8sYfhywY9CsIwJlV+AYDJ9zYIiWCdGyzg5LY9vMRRxf/tYy9oY+KeDhZdBes4kR5A7D5ySYEvYbruYKmZEigB0b9GkOjJZSqbBtDvbgB44VEmISbFPl16kOYf6GCHDbdqqXp8H2AYFiYR9s3gfaDCnuk2AeRTgF2GUkEgkeJ7JLDoxhzuRng2RXdQhWr0iIOTDV3084GH8ERhXudLskymYxetkAuWK+iNBfauixfWtvwxhl19hMxIrXWd+ueTBqL6zCPiF9cMeUvzezRFgNuTpXZ7fEWw5MPZyqQfkbQ4Yt9iOsz4HB1aKvC1+L7trOXi8HNkJ7kQYZLdTeUXWI0sc6J8G0T3hgvpjr3w5vuO5puxi8DNHCRg5FuEUGRNRnwjZOK9kUMXWzUQrr4XTdVzXYMBbNBNkIHo1p1HFbv2TB8Kqwoyjqkc6udKnXorFdTFrkgra4FJeFICPI3xeJbhcbI3O8qF5eNbxUW+Hf3UfLWBdOx9kAixHNaijAl6hvGAvf4gmEHKy4xn+9q1tKgqkBOs4CipljijQYOpYB21R4IJNzymfcJ1Qko11r8pYxP+MGSZybpXFTca16z4FtZysUsls196CE1UTYeaqowvxx6lg8psHGsE/zpahqBw3yL8OE5jEGPM9246Bavd5l6Nc6pgFB+YR9r7CUQ5wkmEZkOtE1Te75gXP0evz4k0CrWJwqxtHJHB+U85zUc2D3/2pu7MZu7MYSY38A9s7te9lBf2oAAAAASUVORK5CYII=\",\n" +
                "        \"corpId\": \"08c3eaac8ca44a6ba6c60d993f985883\",\n" +
                "        \"registerKey\": \"MJKUE32UENUVAYST\",\n" +
                "        \"terms\": 180,\n" +
                "        \"serverUrl\": \"192.168.200.212\",\n" +
                "        \"pushConnectorUrl\": \"ws://192.168.200.212:15010\",\n" +
                "        \"pushConnectorToken\": \"J360409cf90693fc44232fb475cc69e994f70480a974430d005ce32efe0d1b62c3010383a0cb76135c6b58e69fba420260ee5959551660ab6499964122f60385a\",\n" +
                "        \"userId\": \"null\"\n" +
                "    }\n" +
                "}";
    }

    @Override
    public String getToken(String userId) {
        return "{\n" +
                "    \"result\": true,\n" +
                "    \"msg\": \"ok\",\n" +
                "    \"code\": \"000\",\n" +
                "    \"data\": {\n" +
                "        \"token\": \"thrY1DYSy5F4pMlYQ2U2Lf9k+kt1+HPFLjTv1nNb9t1+4EKJlSFEF1vYRl7zDCWI\"\n" +
                "    }\n" +
                "}";
    }

    @Override
    public String getSp(String userId, String clientIp, String sessionId) {
        return "{\n" +
                "    \"result\": true,\n" +
                "    \"msg\": \"OK.\",\n" +
                "    \"code\": \"000.0\",\n" +
                "    \"data\": {\n" +
                "        \"term\": 60,\n" +
                "        \"pushConnectorUrl\": \"ws://192.168.200.212:15010\",\n" +
                "        \"pushConnectorToken\": \"Aabef80304cee551df5c6a200d0e006ab704dcae9e7f083008a0b0c07c054389896784e58331aa3f1f946aa165c797dec6bf04143060809cda157563d937d81442d9a50974ca068ebdcad05c457d94b2d\",\n" +
                "        \"servicePassword\": \"121091\",\n" +
                "        \"userId\": \"null\"\n" +
                "    }\n" +
                "}";
    }

    @Override
    public String checkResult(String userId, String sessionId) {
        return "{\n" +
                "    \"result\": true,\n" +
                "    \"msg\": \"OK.\",\n" +
                "    \"code\": \"000.0\",\n" +
                "    \"data\": {\n" +
                "        \"auth\": \"W\",\n" +
                "        \"userId\": \"null\",\n" +
                "        \"hash\": \"cf32fc1eb1411cbb22cc9d2e53c8c3894cffd86c\"\n" +
                "    }\n" +
                "}";
    }

    @Override
    public String cancel(String userId, String sessionId) {
        return "{\n" +
                "    \"result\": true,\n" +
                "    \"msg\": \"OK.\",\n" +
                "    \"code\": \"000.0\"\n" +
                "}";
    }

    @Override
    public String withdrawalAp(String userId) {
        return "{\n" +
                "    \"result\": true,\n" +
                "    \"msg\": \"OK.\",\n" +
                "    \"code\": \"000.0\"\n" +
                "}";
    }

    @Override
    public PWLTokenResponse verifyAndGenerateTokens(String email, String sessionId) {
        return PWLTokenResponse.builder()
                .accessToken("mock-access-token-for-" + email)
                .refreshToken("mock-refresh-token-for-" + email)
                .build();
    }
}
