enum Role {
  superAdmin('SUPER_ADMIN'),
  owner('OWNER'),
  agent('AGENT');

  const Role(this.api);

  final String api;

  static Role fromApi(String value) => Role.values.firstWhere((r) => r.api == value);
}

class AreaRef {
  const AreaRef({required this.id, required this.name});

  final int id;
  final String name;

  factory AreaRef.fromJson(Map<String, dynamic> json) =>
      AreaRef(id: (json['id'] as num).toInt(), name: json['name'] as String);
}

class AppUser {
  const AppUser({required this.id, required this.name, required this.phone, required this.role, required this.areas});

  final int id;
  final String name;
  final String phone;
  final Role role;

  /// Areas assigned to an agent; empty for owners (who see every area).
  final List<AreaRef> areas;

  bool get isOwner => role == Role.owner;

  factory AppUser.fromJson(Map<String, dynamic> json) => AppUser(
        id: (json['id'] as num).toInt(),
        name: json['name'] as String,
        phone: json['phone'] as String,
        role: Role.fromApi(json['role'] as String),
        areas: ((json['areas'] as List?) ?? const [])
            .map((a) => AreaRef.fromJson(a as Map<String, dynamic>))
            .toList(),
      );
}

class TokenResponse {
  const TokenResponse({required this.accessToken, required this.refreshToken, required this.userJson});

  final String accessToken;
  final String refreshToken;

  /// Kept raw so it can be stored and re-parsed on the next app start.
  final Map<String, dynamic> userJson;

  AppUser get user => AppUser.fromJson(userJson);

  factory TokenResponse.fromJson(Map<String, dynamic> json) => TokenResponse(
        accessToken: json['accessToken'] as String,
        refreshToken: json['refreshToken'] as String,
        userJson: json['user'] as Map<String, dynamic>,
      );
}
