package com.example.nextlist.core.result

enum class AppOperation {
    LOGIN,
    REGISTER,
    PASSWORD_RESET,
    EMAIL_VERIFICATION,
    PROFILE_SAVE,
    AVATAR_UPLOAD,
    GENERAL,
}

fun AppError.toUserMessage(operation: AppOperation = AppOperation.GENERAL): String = when (this) {
    AppError.UNAUTHENTICATED -> if (operation == AppOperation.LOGIN) {
        "邮箱或密码不正确"
    } else {
        "登录状态已失效，请重新登录"
    }
    AppError.PERMISSION_DENIED -> "没有权限完成此操作"
    AppError.NETWORK_UNAVAILABLE -> "网络不可用，请检查连接后重试"
    AppError.NOT_FOUND -> if (operation == AppOperation.LOGIN) {
        "邮箱或密码不正确"
    } else {
        "没有找到需要的内容"
    }
    AppError.ALREADY_EXISTS -> if (operation == AppOperation.REGISTER) {
        "这个邮箱已经注册，可以直接登录"
    } else {
        "内容已经存在"
    }
    AppError.GROUP_FULL -> "小组成员已满"
    AppError.GROUP_DISSOLVED -> "小组已解散，无法继续访问"
    AppError.INVITE_INVALID -> "邀请无效，请检查后重试"
    AppError.INVITE_EXPIRED -> "邀请已失效或过期"
    AppError.EMAIL_NOT_VERIFIED -> "请先验证邮箱，再创建或加入小组"
    AppError.NOT_ADMIN -> "只有小组管理员可以完成此操作"
    AppError.ADMIN_CANNOT_LEAVE -> "管理员需要先转让管理员身份，或解散小组"
    AppError.TARGET_NOT_MEMBER -> "该成员已不在小组中"
    AppError.CONFLICT -> "内容已被其他成员更新，请确认最新内容后重试"
    AppError.VALIDATION -> when (operation) {
        AppOperation.AVATAR_UPLOAD -> "头像文件无效，请重新选择图片"
        AppOperation.REGISTER -> "请检查注册信息后重试"
        AppOperation.PROFILE_SAVE -> "请检查昵称或头像后重试"
        else -> "请检查填写的内容"
    }
    AppError.RATE_LIMITED -> "操作过于频繁，请稍后再试"
    AppError.UNKNOWN -> "暂时无法完成操作，请稍后重试"
}
