import 'package:riverpod_annotation/riverpod_annotation.dart';

part 'nested_provider.g.dart';

@riverpod
String nestedValue(Ref ref) => 'nested';
