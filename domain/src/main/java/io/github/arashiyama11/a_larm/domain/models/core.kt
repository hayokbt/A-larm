package io.github.arashiyama11.a_larm.domain.models

@JvmInline
value class AlarmId(val value: Long)

@JvmInline
value class SessionId(val value: String)

enum class Role { System, Assistant, User }

