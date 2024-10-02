package com.example.codemangesystem.LoginProcess.services;

import com.example.codemangesystem.LoginProcess.model_response.LoginINFO;
import com.example.codemangesystem.LoginProcess.model_response.LoginResponse;
import com.example.codemangesystem.LoginProcess.model_user.MyUser;
import com.example.codemangesystem.LoginProcess.model_response.RegisterResponse;
import com.example.codemangesystem.LoginProcess.model_user.UserAuthority;
import com.example.codemangesystem.LoginProcess.repository.MyUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class MyUserService {

    private static final Logger logger = LoggerFactory.getLogger(MyUserService.class);
    private final MyUserRepository myUserRepository;
    private final PasswordBcrypt passwordBcrypt;

    @Autowired
    public MyUserService(MyUserRepository myUserRepository, PasswordBcrypt passwordBcrypt) {
        this.myUserRepository = myUserRepository;
        this.passwordBcrypt = passwordBcrypt;
    }

    // 這個為自身簡單加入超級帳號使用
    public void AddSuperAccount() {
        MyUser myUser = MyUser.builder()
                .userEmail("zz@gmail.com")
                .userName("Admin")
                .userAccount("123")
                .userPassword("123")
                .userAuthority(UserAuthority.ADMIN)
                .build();
        myUser.setUserPassword(passwordBcrypt.encryptPassword(myUser.getUserPassword()));

        myUserRepository.save(myUser);
    }

    // Check User
    public LoginResponse checkUser(LoginINFO UserINFO) {
        Optional<MyUser> testCurrentHave = myUserRepository.findByUserAccount(UserINFO.getUserAccount());
        logger.info(UserINFO.getUserAccount());

        if (testCurrentHave.isPresent()) {
            logger.info("Account have " + testCurrentHave);

            if (!passwordBcrypt.decryptPasswordIsSameOrNot(testCurrentHave.get().getUserPassword(), UserINFO.getUserPassword())) {
                logger.info("Has this email but the password wrong");

                return LoginResponse.builder()
                        .message("Password Wrong")
                        .success(false)
                        .build();
            }
        } else {
            logger.info("No this email");

            return LoginResponse.builder()
                    .message("No this email")
                    .success(false)
                    .build();
        }

        return LoginResponse.builder()
                .message("Success")
                .success(true)
                .myUser(testCurrentHave.get())
                .build();
    }

    // Add User
    public RegisterResponse userRegister(MyUser myUser) {
        try {
            Optional<MyUser> testCurrentHave = myUserRepository.findByUserEmail(myUser.getUserEmail());
            if (testCurrentHave.isPresent()) {
                logger.info("email is taken" + testCurrentHave);

                return RegisterResponse.builder()
                        .success(false)
                        .message("email is taken")
                        .build();
            }

            // 為密碼加密處理 bcrypt
            myUser.setUserPassword(passwordBcrypt.encryptPassword(myUser.getUserPassword()));
            // 設定權限
            myUser.setUserAuthority(UserAuthority.OWNER);

            myUserRepository.save(myUser);
            logger.info("Register success");

            return RegisterResponse.builder()
                    .success(true)
                    .message("success register")
                    .build();
        } catch (Exception e) {
            logger.info("Register failed " + e.getMessage());

            return RegisterResponse.builder()
                    .success(false)
                    .message("Failed " + e.getMessage())
                    .build();
        }
    }
}
