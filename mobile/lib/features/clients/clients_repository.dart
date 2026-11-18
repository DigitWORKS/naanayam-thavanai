import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_error.dart';
import '../../core/providers.dart';
import 'client_models.dart';

final clientsRepositoryProvider = Provider<ClientsRepository>((ref) => ClientsRepository(ref));

/// Client detail; invalidate after a change (new loan, payment) to refresh.
final clientDetailProvider = FutureProvider.autoDispose.family<ClientDetail, int>((ref, id) {
  return ref.watch(clientsRepositoryProvider).get(id);
});

/// Number of stored documents for a client.
final clientDocumentCountProvider = FutureProvider.autoDispose.family<int, int>((ref, id) {
  final dio = ref.watch(apiClientProvider);
  return ApiError.guard(() async {
    final res = await dio.get<List<dynamic>>('/clients/$id/documents');
    return res.data!.length;
  });
});

class ClientsRepository {
  ClientsRepository(this._ref);

  final Ref _ref;

  Future<ClientPage> search({String? q, int? areaId, ClientFilter filter = ClientFilter.all, int page = 0, int size = 30}) =>
      ApiError.guard(() async {
        final res = await _ref.read(apiClientProvider).get<Map<String, dynamic>>('/clients', queryParameters: {
          if (q != null && q.trim().isNotEmpty) 'q': q.trim(),
          if (areaId != null) 'areaId': areaId,
          'filter': filter.api,
          'page': page,
          'size': size,
        });
        return ClientPage.fromJson(res.data!);
      });

  Future<ClientDetail> get(int id) => ApiError.guard(() async {
        final res = await _ref.read(apiClientProvider).get<Map<String, dynamic>>('/clients/$id');
        return ClientDetail.fromJson(res.data!);
      });

  /// Returns the new client's id. A 409 CLIENT_EXISTS carries the existing client in [ApiError.details].
  Future<int> create(NewClientInput input) => ApiError.guard(() async {
        final res = await _ref.read(apiClientProvider).post<Map<String, dynamic>>('/clients', data: input.toJson());
        return (res.data!['id'] as num).toInt();
      });
}
