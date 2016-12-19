import actors.{PlayerActor, ScoresActor, UserParentActor}
import com.google.inject.AbstractModule
import play.api.libs.concurrent.AkkaGuiceSupport

class Module extends AbstractModule with AkkaGuiceSupport {
  override def configure(): Unit = {
    bindActor[UserParentActor]("userParentActor")
    bindActor[ScoresActor]("scoresPersisterActor")
    bindActorFactory[PlayerActor, PlayerActor.Factory]
  }
}
