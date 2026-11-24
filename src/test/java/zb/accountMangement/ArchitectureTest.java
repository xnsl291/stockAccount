package zb.accountMangement;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import zb.accountMangement.account.model.entity.Account;
import zb.accountMangement.account.repository.AccountRepository;
import zb.accountMangement.account.repository.SystemEntryRepository;
import zb.accountMangement.account.repository.TransactionRepository;
import zb.accountMangement.account.service.AccountService;
import zb.accountMangement.account.service.TransactionService;
import zb.accountMangement.stock.repository.StockBalanceRepository;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * 모듈(최상위 패키지) 사이에 누가 누구를 import해도 되는지 고정하는 테스트.
 * 지금 있는 모듈(auth, account, member, stock, idempotency, common)에만 규칙을 걸고, 새 모듈이 생기면 그때 규칙을 더함.
 * 없는 패키지에 규칙을 걸면 검사할 클래스가 없어 항상 통과하므로 미리 넣지 않음
 */
class ArchitectureTest {

	private static final String BASE = "zb.accountMangement";

	private static final JavaClasses CLASSES = new ClassFileImporter()
			.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
			.importPackages(BASE);

	// 허용한 예외 하나: 계좌 해지·탈퇴 때 AccountService가 보유 주식 행을 잠가 읽으려고 이 저장소를 직접 부름.
	// 주식 서비스가 이미 AccountService를 주입받고 있어 반대로 서비스를 주입하면 빈이 서로 물리기 때문임.
	// 부르는 쪽(AccountService)과 불리는 쪽(StockBalanceRepository)을 모두 클래스 하나로 못 박아 두어,
	// 계좌 모듈의 다른 클래스가 이 저장소를 쓰거나 AccountService가 주식 쪽 다른 클래스를 쓰면 아래 규칙들이 실패함
	private static final DescribedPredicate<JavaClass> ACCOUNT_TO_STOCK_ORIGIN = equivalentTo(AccountService.class);
	private static final DescribedPredicate<JavaClass> ACCOUNT_TO_STOCK_EXCEPTION = equivalentTo(StockBalanceRepository.class);

	@Test
	void 각_모듈은_허용된_모듈만_import한다() {
		layeredArchitecture()
				.consideringOnlyDependenciesInLayers()
				.layer("auth").definedBy(BASE + ".auth..")
				.layer("stock").definedBy(BASE + ".stock..")
				.layer("account").definedBy(BASE + ".account..")
				.layer("member").definedBy(BASE + ".member..")
				.layer("idempotency").definedBy(BASE + ".idempotency..")
				.layer("common").definedBy(BASE + ".common..")
				// auth는 가입·탈퇴·로그인 순서를 짜는 맨 위 모듈이라 다른 모듈의 부품이 되지 않음
				.whereLayer("auth").mayNotBeAccessedByAnyLayer()
				.whereLayer("auth").mayOnlyAccessLayers("account", "member", "common")
				.whereLayer("stock").mayOnlyAccessLayers("account", "idempotency", "common")
				.whereLayer("account").mayOnlyAccessLayers("member", "idempotency", "common")
				.whereLayer("member").mayOnlyAccessLayers("common")
				// 중복 요청 처리는 계좌·주식 요청을 감싸는 바닥 모듈이라 업무 모듈을 알지 않음
				.whereLayer("idempotency").mayOnlyAccessLayers("common")
				// 공통이 기능 모듈을 알면 의존 방향이 거꾸로 됨
				.whereLayer("common").mayNotAccessAnyLayer()
				.ignoreDependency(ACCOUNT_TO_STOCK_ORIGIN, ACCOUNT_TO_STOCK_EXCEPTION)
				.check(CLASSES);
	}

	// 테이블의 주인 모듈만 그 저장소를 씀.
	// member·idempotency·common은 계층 규칙이 다른 기능 모듈 접근을 아예 막아 여기서 따로 보지 않음
	@ParameterizedTest
	@ValueSource(strings = {"auth", "account", "stock"})
	void 다른_모듈의_저장소를_쓰지_않는다(String module) {
		DescribedPredicate<JavaClass> otherRepository = resideInAPackage(BASE + "..repository..")
				.and(not(resideInAPackage(BASE + "." + module + "..")));
		if (module.equals("account")) {
			noClasses().that().resideInAPackage(BASE + ".account..").and(not(ACCOUNT_TO_STOCK_ORIGIN))
					.should().dependOnClassesThat(otherRepository)
					.check(CLASSES);
			noClasses().that(ACCOUNT_TO_STOCK_ORIGIN)
					.should().dependOnClassesThat(otherRepository.and(not(ACCOUNT_TO_STOCK_EXCEPTION)))
					.check(CLASSES);
			return;
		}

		noClasses().that().resideInAPackage(BASE + "." + module + "..")
				.should().dependOnClassesThat(otherRepository)
				.check(CLASSES);
	}

	// 엔티티 값을 바꿀 때는 주인 모듈이 연 메서드(예: Account.hold)를 씀. 따로 보지 않는 모듈은 위와 같은 이유임
	@ParameterizedTest
	@ValueSource(strings = {"auth", "account", "stock"})
	void 다른_모듈의_엔티티_setter를_부르지_않는다(String module) {
		String ownPackage = BASE + "." + module + ".";
		DescribedPredicate<JavaMethodCall> otherEntitySetter = new DescribedPredicate<JavaMethodCall>("다른 모듈 엔티티의 setter 호출") {
			@Override
			public boolean test(JavaMethodCall call) {
				String targetPackage = call.getTargetOwner().getPackageName();
				return call.getName().startsWith("set")
						&& targetPackage.startsWith(BASE + ".")
						&& targetPackage.endsWith(".model.entity")
						&& !targetPackage.startsWith(ownPackage);
			}
		};

		noClasses().that().resideInAPackage(BASE + "." + module + "..")
				.should().callMethodWhere(otherEntitySetter)
				.check(CLASSES);
	}

	// 계좌 행 잠금의 입구를 하나로 둠
	@Test
	void 계좌_잠금_조회는_AccountService만_부른다() {
		noClasses().that().doNotHaveFullyQualifiedName(AccountService.class.getName())
				.should().callMethodWhere(callTo(AccountRepository.class, "findByIdForUpdate"))
				.check(CLASSES);
	}

	// 잔액은 거래 기록과 함께 바뀌어야 하므로 TransactionService의 잔액 변경을 거치지 않고는 바꿀 수 없음
	@Test
	void 잔액_setter는_TransactionService만_부른다() {
		noClasses().that().doNotHaveFullyQualifiedName(TransactionService.class.getName())
				.should().callMethodWhere(callTo(Account.class, "setBalance"))
				.check(CLASSES);
	}

	// 거래 기록만 남기고 시스템 기록을 빠뜨리는 다른 길이 생기지 않게 함
	@Test
	void 거래_기록과_시스템_기록_저장소는_TransactionService만_쓴다() {
		noClasses().that().doNotHaveFullyQualifiedName(TransactionService.class.getName())
				.should().dependOnClassesThat().belongToAnyOf(TransactionRepository.class, SystemEntryRepository.class)
				.check(CLASSES);
	}

	private static DescribedPredicate<JavaMethodCall> callTo(Class<?> owner, String methodName) {
		return new DescribedPredicate<JavaMethodCall>(owner.getSimpleName() + "." + methodName + " 호출") {
			@Override
			public boolean test(JavaMethodCall call) {
				return call.getTargetOwner().isEquivalentTo(owner) && call.getName().equals(methodName);
			}
		};
	}
}
