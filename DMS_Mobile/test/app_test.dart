import 'package:dms_mobile/documents.dart';
import 'package:dms_mobile/main.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';

void main() {
  testWidgets('lists documents, searches, and loads document detail', (
    tester,
  ) async {
    final api = _FakeApi();
    await tester.pumpWidget(DmsApp(api: api));
    await tester.pumpAndSettle();

    expect(find.text('Unrelated document'), findsOneWidget);
    await tester.enterText(find.byType(TextField), '  Yukon  ');
    await tester.testTextInput.receiveAction(TextInputAction.search);
    await tester.pumpAndSettle();
    expect(api.queries, ['', 'Yukon']);
    expect(find.text('Unrelated document'), findsNothing);

    await tester.tap(find.text('Yukon notice'));
    await tester.pumpAndSettle();
    expect(api.openedId, 11);
    expect(find.text('File: yukon.pdf'), findsOneWidget);
    expect(find.text('Status: Complete'), findsOneWidget);
    expect(
      find.text('The notice explains how to view PDF forms.'),
      findsOneWidget,
    );
  });

  testWidgets('offers retry after a failed request', (tester) async {
    final api = _FakeApi()..failOnce = true;
    await tester.pumpWidget(DmsApp(api: api));
    await tester.pumpAndSettle();
    expect(find.text('Could not load from the DMS server.'), findsOneWidget);

    await tester.tap(find.text('Retry'));
    await tester.pumpAndSettle();
    expect(find.text('Yukon notice'), findsOneWidget);
  });
}

class _FakeApi extends DocumentApi {
  _FakeApi() : super(Uri.parse('https://example.invalid'));

  final queries = <String>[];
  int? openedId;
  bool failOnce = false;

  static const _notice = Document(
    id: 11,
    title: 'Yukon notice',
    fileName: 'yukon.pdf',
  );

  @override
  Future<List<Document>> documents([String query = '']) async {
    queries.add(query);
    if (failOnce) {
      failOnce = false;
      throw StateError('Server unavailable');
    }
    return [
      _notice,
      if (query.isEmpty) const Document(id: 9, title: 'Unrelated document'),
    ];
  }

  @override
  Future<Document> document(int id) async {
    openedId = id;
    return Document.fromJson({
      'id': id,
      'title': _notice.title,
      'fileName': _notice.fileName,
      'status': 'SUMMARY_GENERATED',
      'summary': 'The notice explains how to view PDF forms.',
    });
  }
}
