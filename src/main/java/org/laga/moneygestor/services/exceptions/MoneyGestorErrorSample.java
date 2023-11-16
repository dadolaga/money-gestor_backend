package org.laga.moneygestor.services.exceptions;

import org.springframework.http.HttpStatusCode;

public class MoneyGestorErrorSample {
    public static final HttpException NOT_ALL_FIELD_INSERT = new HttpException(HttpStatusCode.valueOf(400), new Error(1, "not all filed insert"));
    public static final HttpException USER_DUPLICATE_EMAIL = new HttpException(HttpStatusCode.valueOf(400), new Error(101, "duplicate email"));
    public static final HttpException USER_DUPLICATE_USERNAME = new HttpException(HttpStatusCode.valueOf(400), new Error(102, "duplicate username"));
    public static final HttpException USER_EMAIL_USERNAME_NOT_EXIST = new HttpException(HttpStatusCode.valueOf(400), new Error(103, "email or username not exist"));
    public static final HttpException USER_PASSWORD_NOT_CORRECT = new HttpException(HttpStatusCode.valueOf(400), new Error(104, "password not correct"));
}
