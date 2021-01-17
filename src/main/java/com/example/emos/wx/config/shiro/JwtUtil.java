package com.example.emos.wx.config.shiro;

import cn.hutool.core.date.DateField;
import cn.hutool.core.date.DateUtil;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.example.emos.wx.exception.EmosException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
@Slf4j
public class JwtUtil {
    @Value("${emos.jwt.secret}")
    private String secret;

    @Value("${emos.jwt.expire}")
    private int expire;

    public String createToken(int userId) {
        // shift expiration date from cur date
        Date date = DateUtil.offset(new Date(), DateField.DAY_OF_YEAR, expire).toJdkDate();
        // encode secret using algorithm
        Algorithm algorithm = Algorithm.HMAC256(secret);
        // using inner class to generate tokens
        JWTCreator.Builder builder= JWT.create();
        return builder.withClaim("userId", userId).withExpiresAt(date).sign(algorithm);
    }

    public int getUserId(String token) {
        // decode token to get userId
        try {
            DecodedJWT decode = JWT.decode(token);
            return decode.getClaim("userId").asInt();
        } catch (Exception e) {
            throw new EmosException("令牌无效");
        }
    }

    public void verifierToken(String token) {
        // verify the token with secret
        // check if token was encoded by secret
        Algorithm algorithm = Algorithm.HMAC256(secret);
        JWTVerifier verifier = JWT.require(algorithm).build();
        // Throw runtime exception if failed
        verifier.verify(token);
    }
}
