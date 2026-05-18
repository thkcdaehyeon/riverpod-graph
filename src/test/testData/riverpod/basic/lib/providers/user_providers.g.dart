// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'user_providers.dart';

final currentUserProvider = AutoDisposeProvider<String>((ref) => 'guest');
final userProvider = AutoDisposeFutureProviderFamily<User, String>((ref, id) async => User(id));
final profileProvider = AutoDisposeProvider<Profile>((ref) => Profile(User('42')));
final selectedUserNameProvider = AutoDisposeProvider<String>((ref) => '');
