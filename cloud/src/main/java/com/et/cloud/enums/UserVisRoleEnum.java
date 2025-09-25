package com.et.cloud.enums;//package com.itheima.user.enums;
//
//import lombok.Getter;
//
//@Getter
//public class UserVisRoleEnum {
//
//    USER("用户", "user"),
//    ADMIN("管理员", "admin");
//
//    private final String text;
//    private final String value;
//
//    UserVisRoleEnum(String text, String value) {
//        this.text = text;
//        this.value = value;
//    }
//
//    //    根据value获取枚举
//    public static UserVisRoleEnum getUserRoleEnum(String value) {
//        if (value == null){
//            return null;
//        }
//        for (UserVisRoleEnum e : UserVisRoleEnum.values()) {
//            if (e.value.equals(value)) {
//                return e;
//            }
//        }
//        return null;
//    }
//}