import 'dart:convert';
import 'dart:io';

class Document {
  const Document({
    required this.id,
    required this.title,
    this.description,
    this.fileName,
    this.fileSize,
    this.contentType,
    this.status,
    this.summary,
  });

  factory Document.fromJson(Map<String, dynamic> json) {
    String? optionalText(String key) {
      final value = (json[key] as String?)?.trim();
      return value == null || value.isEmpty ? null : value;
    }

    return Document(
      id: json['id'] as int,
      title: json['title'] as String,
      description: optionalText('description'),
      fileName: optionalText('fileName'),
      fileSize: json['fileSize'] as int?,
      contentType: optionalText('contentType'),
      status: optionalText('status'),
      summary: optionalText('summary'),
    );
  }

  final int id;
  final String title;
  final String? description, fileName, contentType, status, summary;
  final int? fileSize;

  String? get statusLabel => switch (status) {
    'UPLOADED' => 'Uploaded',
    'PROCESSING' => 'Processing',
    'OCR_COMPLETE' => 'OCR complete',
    'SUMMARIZING' => 'Summarizing…',
    'SUMMARY_GENERATED' => 'Complete',
    'ARCHIVED' => 'Archived',
    _ => status,
  };
}

class DocumentApi {
  const DocumentApi(this.baseUrl);

  final Uri baseUrl;

  Future<List<Document>> documents([String query = '']) async {
    final json = query.isEmpty
        ? await _get('/api/documents')
        : await _get('/api/documents/search', {'q': query});
    return (json as List)
        .map((entry) => Document.fromJson(entry as Map<String, dynamic>))
        .toList();
  }

  Future<Document> document(int id) async {
    return Document.fromJson(
      await _get('/api/documents/$id') as Map<String, dynamic>,
    );
  }

  Future<Object?> _get(String path, [Map<String, String>? query]) async {
    const timeout = Duration(seconds: 10);
    final client = HttpClient()..connectionTimeout = timeout;
    final uri = baseUrl.resolve(path).replace(queryParameters: query);
    try {
      final request = await client.getUrl(uri).timeout(timeout);
      request.followRedirects = false;
      final response = await request.close().timeout(timeout);
      if (response.statusCode != HttpStatus.ok) {
        throw HttpException('HTTP ${response.statusCode}', uri: uri);
      }
      return jsonDecode(
        await response.transform(utf8.decoder).join().timeout(timeout),
      );
    } finally {
      client.close(force: true);
    }
  }
}
