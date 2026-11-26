package com.wanyue.cloud.modules.auth.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

@Data
public class EmailRegisterReqVO {

    @NotBlank(message="邮箱不能为空")
    @Email(message="邮箱格式不正确")
    @Length(max=191, message="邮箱长度不能超过191个字符")
    private String email;

    @NotBlank(message="验证码不能为空")
    @Pattern(regexp="[0-9]{6}", message="验证码必须为6位数字")
    private String code;

    @NotBlank(message="密码不能为空")
    @Length(min = 8,max = 32, message="密码长度为8-32个字符")
    private String password;

}
