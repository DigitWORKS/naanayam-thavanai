import 'package:dio/dio.dart';

/// The API's Error schema ({code, message, details}), or a local/network failure.
class ApiError implements Exception {
  const ApiError({this.status, required this.code, required this.message, this.details = const {}});

  /// HTTP status; null when no response arrived (offline, timeout).
  final int? status;
  final String code;
  final String message;
  final Map<String, dynamic> details;

  /// True when the request may or may not have reached the server.
  bool get isNetwork => status == null;

  factory ApiError.fromDio(DioException e) {
    final res = e.response;
    if (res == null) {
      return const ApiError(
        code: 'NETWORK',
        message: "Can't reach the server. Check the internet connection.",
      );
    }
    final data = res.data;
    if (data is Map && data['code'] is String) {
      return ApiError(
        status: res.statusCode,
        code: data['code'] as String,
        message: (data['message'] as String?) ?? 'Something went wrong',
        details: data['details'] is Map ? Map<String, dynamic>.from(data['details'] as Map) : const {},
      );
    }
    return ApiError(status: res.statusCode, code: 'HTTP_${res.statusCode}', message: 'Server error (${res.statusCode})');
  }

  /// Runs [call] and converts Dio failures into [ApiError].
  static Future<T> guard<T>(Future<T> Function() call) async {
    try {
      return await call();
    } on DioException catch (e) {
      throw e.error is ApiError ? e.error as ApiError : ApiError.fromDio(e);
    }
  }

  @override
  String toString() => 'ApiError($status, $code, $message)';
}
