package com.product.globie.service.impl;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.product.globie.entity.User;
import com.product.globie.entity.Role;
import com.product.globie.exception.AppException;
import com.product.globie.exception.ErrorCode;
import com.product.globie.payload.request.IntrospectRequest;
import com.product.globie.payload.response.AuthenticationResponse;
import com.product.globie.payload.request.AuthenticationRequest;
import com.product.globie.payload.request.SignUpRequest;
import com.product.globie.payload.response.IntrospectResponse;
import com.product.globie.repository.AccountRepository;
import com.product.globie.repository.RoleRepository;
import com.product.globie.service.AuthenticationService;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class AuthenticationServiceImpl implements AuthenticationService {
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private final ModelMapper mapper;
    @Autowired
    private RoleRepository roleRepository;


    @Value("${app.jwt-secret}")
    private String SIGNER_KEY;

    @Value( "${app.jwt-access-expiration-milliseconds}")
    private long VALID_DURATION;

    @Value("${app.jwt-refresh-expiration-milliseconds}")
    private long REFRESHABLE_DURATION;

    public AuthenticationServiceImpl(ModelMapper mapper) {
        this.mapper = mapper;
    }

    private String generateToken(User user) {
        JWSHeader header = new JWSHeader(JWSAlgorithm.HS512);

        JWTClaimsSet jwtClaimsSet = new JWTClaimsSet.Builder()
                .subject(user.getUserName())
                .issuer("Mercury.com")
                .issueTime(new Date())
                .expirationTime(new Date(
                        Instant.now().plus(VALID_DURATION, ChronoUnit.SECONDS).toEpochMilli()
                ))
                .jwtID(UUID.randomUUID().toString())
                .claim("scope", buildScope(user))
                .build();

        Payload payload = new Payload(jwtClaimsSet.toJSONObject());

        JWSObject jwsObject = new JWSObject(header, payload);

        try {
            jwsObject.sign(new MACSigner(SIGNER_KEY.getBytes()));
            return jwsObject.serialize();
        } catch (JOSEException e) {
            throw new RuntimeException(e);
        }
    }
    private String buildScope(User user) {
        Role role = user.getRole();
        if (role != null) {
            return role.getRoleName().name(); // Assuming roleName is an enum or string
        }
        return "";
    }

    @Override
    public AuthenticationResponse login(AuthenticationRequest request) {
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);
        String encodedPassword = passwordEncoder.encode(request.getPassword());
        Optional<User> optionalUser = accountRepository
                .findAccountByUserNameOrEmailOrPhone(request.getUserName(), request.getUserName(), request.getUserName());
        User user = optionalUser.orElseThrow(() -> new AppException(ErrorCode.UNABLE_TO_LOGIN));
        boolean authenticated = passwordEncoder.matches(request.getPassword(), user.getPassword());

        if (!authenticated) throw new AppException(ErrorCode.PASSWORD_NOT_CORRECT);

        var token = generateToken(user);
        Date expirationInstant = new Date(
                Instant.now().plus(VALID_DURATION, ChronoUnit.SECONDS).toEpochMilli()
        );



        return AuthenticationResponse.builder()
                .token(token)
                .authenticated(true)
                .build();
    }

    @Override
    public AuthenticationResponse register(SignUpRequest request) {
        if (accountRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new AppException(ErrorCode.EMAIL_TAKEN);
        }

        // Kiểm tra xem số điện thoại đã tồn tại chưa
        if (accountRepository.findByPhone(request.getPhone()).isPresent()) {
            throw new AppException(ErrorCode.PHONE_TAKEN);
        }

        // Kiểm tra xem tên người dùng đã tồn tại chưa
        if (accountRepository.findByUserName(request.getUserName()).isPresent()) {
            throw new AppException(ErrorCode.USER_EXISTED);
        }
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);
        // Mã hóa mật khẩu trước khi lưu vào cơ sở dữ liệu
        String encodedPassword = passwordEncoder.encode(request.getPassword());
        request.setPassword(encodedPassword);
        User user = mapper.map(request, User.class);
        Role userRole = roleRepository.findRoleByRoleId(4)
                .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
        user.setRole(userRole);
        user.setStatus(false);
        accountRepository.save(user);
        return AuthenticationResponse.builder()
                .token(generateToken(user))
                .authenticated(true)
                .build();

    }

    @Override
    public IntrospectResponse introspect(IntrospectRequest request) throws JOSEException, ParseException {
        var token = request.getToken();


        boolean isValid = true;
        try {
            verifyToken(token,false);
        } catch (AppException e){
            isValid = false;
        }

        return IntrospectResponse.builder().valid(isValid).build();
    }

    @Override
    public void logout(com.product.globie.payload.request.LogoutRequest logoutRequest) {
        try {
            var signToken = verifyToken(logoutRequest.getToken(), true);

            // Optionally, you can log the logout event or perform any necessary cleanup here.
            log.info("User logged out with token ID: {}", signToken.getJWTClaimsSet().getJWTID());
        } catch (AppException exception) {
            log.info("Token already expired");
        } catch (ParseException e) {
            throw new RuntimeException(e);
        } catch (JOSEException e) {
            throw new RuntimeException(e);
        }
    }



    private SignedJWT verifyToken(String token, boolean isRefresh) throws JOSEException, ParseException {
        JWSVerifier verifier = new MACVerifier(SIGNER_KEY.getBytes());
        SignedJWT signedJWT = SignedJWT.parse(token);

        // Get claims
        JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
        Date expirationTime = claims.getExpirationTime();
        Date issueTime = claims.getIssueTime();

        // Determine expiry time
        Date validUntil = isRefresh
                ? new Date(issueTime.toInstant().plus(REFRESHABLE_DURATION, ChronoUnit.SECONDS).toEpochMilli())
                : expirationTime;

        // Verify the token
        boolean verified = signedJWT.verify(verifier);

        // Check expiration
        if (!(verified && validUntil.after(new Date()))) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        return signedJWT;
    }

}
